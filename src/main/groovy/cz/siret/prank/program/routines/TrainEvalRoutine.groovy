package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.program.ml.ClassifierFactory
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.score.metrics.ClassifierStats
import cz.siret.prank.score.prediction.PointScoreCalculator
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances

import static cz.siret.prank.score.prediction.PointScoreCalculator.predictedPositive
import static cz.siret.prank.score.prediction.PointScoreCalculator.predictedScore
import static cz.siret.prank.utils.ATimer.startTimer

@Slf4j
@CompileStatic
class TrainEvalRoutine extends EvalRoutine implements Parametrized  {

    Dataset trainDataSet
    Dataset evalDataSet
    String label

    boolean deleteModel = params.delete_models
    boolean deleteVectors = params.delete_vectors

    Instances trainVectors

    int train_positives
    int train_negatives

    // may be same between iterations
    private String trainVectorFile
    private String evalVectorFile

    EvalModelRoutine evalRoutine

    TrainEvalRoutine(String outdir, Dataset trainData, Dataset evalData) {
        super(outdir)
        this.trainDataSet = trainData
        this.evalDataSet = evalData
    }

    EvalResults execute() {

        collectTrainVectors()
        EvalResults res = trainAndEvalModel()

        if (deleteVectors)
            deleteVectorFiles()

        return res
    }

    void collectTrainVectors() {
        String vectf =  "$outdir/vectorsTrain.arff"
        trainVectorFile = vectf
        trainVectors = doCollectVectors(trainDataSet, vectf)
    }

    /**
     * doesn't store any data, just writes them to file for further analysis
     */
    void collectEvalVectors() {
        String vectf =  "$outdir/vectorsEval.arff"
        evalVectorFile = vectf
        doCollectVectors(evalDataSet, vectf)
    }

    private Instances doCollectVectors(Dataset dataSet, String vectFileName) {
        ATimer timer = startTimer();

        Futils.mkdirs(outdir)

        CollectVectorsRoutine collector = new CollectVectorsRoutine(dataSet, outdir, vectFileName)

        def res = collector.collectVectors()
        Instances inst = res.instances
        train_positives = res.positives
        train_negatives = res.negatives

        logTime "vectors collected in " + timer.formatted

        return inst
    }

    void deleteVectorFiles() {
        Futils.delete(trainVectorFile)
        Futils.delete(evalVectorFile)
    }

    ClassifierStats calculateTrainStats(Classifier classifier, Instances trainVectors) {
        if (params.classifier_train_stats) {
            ClassifierStats trainStats = new ClassifierStats()
            for (Instance inst : trainVectors) {
                double[] hist = classifier.distributionForInstance(inst)
                double score = predictedScore(hist)
                boolean predicted = predictedPositive(score)
                boolean observed = inst.classValue() > 0

                trainStats.addPrediction(observed, predicted, score, hist)
            }
            return trainStats
        } else {
            return null
        }
    }

    EvalResults trainAndEvalModel() {
        def timer = startTimer()

        new File(outdir).mkdirs()

        Classifier classifier = ClassifierFactory.createClassifier(params)
        String classifierLabel = "${classifier.class.simpleName}"
        String modelf = "$outdir/${classifierLabel}.model"

        if (trainVectors==null) {
            trainVectors = WekaUtils.loadData(trainVectorFile)
        }

        write "training classifier ${classifier.getClass().name} on dataset with ${trainVectors.size()} instances"

        WekaUtils.trainClassifier(classifier, trainVectors)
        long trainTime = timer.time
        if (!params.delete_models) {
            WekaUtils.saveClassifier(classifier, modelf)
            write "model saved to file $modelf (${Futils.sizeMBFormatted(modelf)} MB)"
        }

        ClassifierStats trainStats = calculateTrainStats(classifier, trainVectors)

        List<Double> featureImportances = null

        // feature importances
        if (params.feature_importances) {
            if (classifier instanceof  FastRandomForest) {
                featureImportances = (classifier as FastRandomForest).featureImportances.toList()
            } else if (classifier instanceof FasterForest) {
                featureImportances = (classifier as FasterForest).featureImportances.toList()
            }
            if (featureImportances != null) {
                List<String> names = FeatureExtractor.createFactory().vectorHeader

                Writer file = Futils.getWriter("$outdir/feature_importances.csv")
                file << names.join(',') << "\n"
                file << CSV.fromDoubles(featureImportances) << "\n"
                file.close()
                // TODO: calculate variance of feature importances over seedloop iterations
            }
        }

        logTime "model trained in " + timer.formatted

        timer.restart()

        evalRoutine = new EvalModelRoutine(evalDataSet, classifier, classifierLabel, outdir)
        EvalResults res = evalRoutine.execute()
        res.trainTime = trainTime
        res.train_positives = train_positives
        res.train_negatives = train_negatives
        res.featureImportances = featureImportances
        res.classifierTrainStats = trainStats


        logTime "evaluation routine on dataset [$evalDataSet.name] finished in " + timer.formatted

        if (deleteModel)
            Futils.delete(modelf)

        return res
    }



}
