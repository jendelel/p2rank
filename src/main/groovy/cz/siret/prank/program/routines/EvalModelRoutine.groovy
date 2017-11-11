package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.rendering.PyMolRenderer
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.score.*
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs

/**
 * Evaluate a model on a dataset
 * (when rescoring evaluate rescorer)
 */
@Slf4j
class EvalModelRoutine extends EvalRoutine {

    Dataset dataset
    Classifier classifier
    String label
    EvalResults results

    EvalModelRoutine(Dataset dataSet, Classifier classifier, String classifierLabel, String outdir) {
        super(outdir)
        this.dataset = dataSet
        this.classifier = classifier
        this.label = classifierLabel
    }

    EvalModelRoutine(Dataset dataSet, String modelf, String outdir) {
        this(dataSet, WekaUtils.loadClassifier(modelf), Futils.shortName(modelf), outdir)
    }

    private PocketRescorer createRescorer(PredictionPair pair, FeatureExtractor extractor) {
        PocketRescorer rescorer
        switch ( params.rescorer ) {
            case "WekaSumRescorer":
                rescorer = new  WekaSumRescorer(classifier, extractor)
                rescorer.collectStats(pair.queryProtein)
                break
            case "PLBIndexRescorer":
                rescorer = new PLBIndexRescorer()
                break
            case "PocketVolumeRescorer":
                rescorer = new PocketVolumeRescorer()
                break
            case "RandomRescorer":
                rescorer = new RandomRescorer()
                break
            case "IdentityRescorer":
                rescorer = new IdentityRescorer()
                break
            default:
                throw new RuntimeException("Invalid rescorer [$params.rescorer]!")
        }
        return rescorer
    }

    @Override
    EvalResults execute() {
        def timer = startTimer()

        write "evaluating results on dataset [$dataset.name]"
        mkdirs(outdir)
        writeParams(outdir)

        String visDir = "$outdir/visualizations"
        if (params.visualizations) {
            mkdirs(visDir)
        }

        String orig_pockets_dir = "$outdir/original_pockets"
        if (!params.predictions) {
            mkdirs(orig_pockets_dir)
        }

        results = new EvalResults(1)
        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result datasetResult = dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {
                PredictionPair pair = item.predictionPair

                PocketRescorer rescorer = createRescorer(pair, extractor)
                rescorer.reorderPockets(pair.prediction, item.context)

                if (params.visualizations) {
                    new PyMolRenderer(visDir).visualizeHistograms(item, (WekaSumRescorer)rescorer, pair)
                }

                if (params.predictions) {
                    results.eval.addPrediction(pair, pair.prediction.pockets)
                } else { // rescore
                    results.eval.addPrediction(pair, pair.prediction.reorderedPockets)
                    results.origEval.addPrediction(pair, pair.prediction.pockets)

                    String originalPocketsStr = pair.prediction.pockets.collect { Pocket p ->
                        "$p.rank  $p.score  $p.name  $p.centroid.x  $p.centroid.y  $p.centroid.z".replace("  ", "\t")
                    }.join("\n")

                    Futils.writeFile("$orig_pockets_dir/${pair.name}_pockets.txt", originalPocketsStr)
                }

                if (rescorer instanceof WekaSumRescorer) {
                    synchronized (results.classifierStats) {
                        results.classifierStats.addAll(rescorer.stats)
                    }
                }

                if (!dataset.cached) {
                    item.cachedPair = null
                }
            }
        });

        results.logAndStore(outdir, classifier.class.simpleName)
        logSummaryResults(dataset.label, label, results)

        write "processed $results.origEval.ligandCount ligands in $dataset.size files"
        logTime "model evaluation finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        results.evalTime = timer.time
        results.datasetResult = datasetResult

        return results
    }

}
