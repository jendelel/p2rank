package cz.siret.prank.score

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.rendering.LabeledPoint
import cz.siret.prank.score.metrics.ClassifierStats
import cz.siret.prank.score.prediction.PocketPredictor
import cz.siret.prank.score.prediction.PointScoreCalculator
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import weka.classifiers.Classifier
import weka.core.DenseInstance
import weka.core.Instances

import static cz.siret.prank.score.prediction.PointScoreCalculator.predictedPositive
import static cz.siret.prank.score.prediction.PointScoreCalculator.predictedScore

/**
 * rescorer and predictor
 * 
 * Not thread safe!
 *
 * This is the main rrescore used by P2RANK to make predictions based on machine learning
 */
@Slf4j
@CompileStatic
class WekaSumRescorer extends PocketRescorer implements Parametrized  {

    private final double POSITIVE_POINT_LIGAND_DISTANCE = params.positive_point_ligand_distance

    private final PointScoreCalculator pointScoreCalculator = new PointScoreCalculator()

    private FeatureExtractor extractorFactory
    private Classifier  classifier
    private ClassifierStats stats = new ClassifierStats()

    boolean collectPoints = params.visualizations || params.predictions
    boolean visualizeAllSurface = params.vis_all_surface

    // SAS points with ligandability score for prediction and visualization
    List<LabeledPoint> labeledPoints = new ArrayList<>()

    // auxiliary for weka
    private Instances auxWekaDataset
    private double[] alloc
    private DenseInstance auxInst

    WekaSumRescorer(Classifier classifier, FeatureExtractor extractorFactory) {
        this.extractorFactory = extractorFactory
        this.classifier = classifier

        auxWekaDataset = WekaUtils.createDatasetWithBinaryClass(extractorFactory.vectorHeader)
    }

    /**
     * @param prediction
     * @param ligandedProtein can be null - in not used for evaluating statistics
     */
    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {

        FeatureExtractor proteinExtractor = extractorFactory.createPrototypeForProtein(prediction.protein, context)

        alloc = new double[proteinExtractor.vectorHeader.size() + 1] // one additional for stupid weka class
        auxInst = new DenseInstance( 1, alloc )
        auxInst.setDataset(auxWekaDataset)

        // PRANK (just rescoring existing pockets)
        if (!params.predictions) {
            doRescore(prediction, proteinExtractor)
        }

        // compute ligandability scores of connolly points for predictions and visualization
        if (params.predictions || visualizeAllSurface) {

            FeatureExtractor extractor = (proteinExtractor as PrankFeatureExtractor).createInstanceForWholeProtein()

            labeledPoints = new ArrayList<>(extractor.sampledPoints.count)
            for (Atom point in extractor.sampledPoints) {
                labeledPoints.add(new LabeledPoint(point))
            }

            for (LabeledPoint point in labeledPoints) {

                // classification

                FeatureVector props = extractor.calcFeatureVector(point.point)
                double[] hist = getDistributionForPoint(classifier, props)

                // labels and statistics

                double predictedScore = predictedScore(hist)   // not all classifiers give histogram that sums up to 1
                boolean predicted = predictedPositive(predictedScore)
                boolean observed = false

                if (ligandAtoms!=null) {
                    double closestLigandDistance = ligandAtoms.count > 0 ? ligandAtoms.dist(point.point) : Double.MAX_VALUE
                    observed = (closestLigandDistance <= POSITIVE_POINT_LIGAND_DISTANCE)
                }

                point.@hist = hist
                point.predicted = predicted
                point.observed = observed

                if (collectingStatistics) {
                    stats.addPrediction(observed, predicted, predictedScore, hist)
                }
            }

            // generate predictions
            if (params.predictions) {
                prediction.pockets = new PocketPredictor().predictPockets(labeledPoints, prediction.protein)
                prediction.reorderedPockets = prediction.pockets
                prediction.labeledPoints = labeledPoints
            }
        }

        proteinExtractor.finalizeProteinPrototype()

    }

    /**
     * Rescore predictions of other method
     */
    private void doRescore(Prediction prediction, FeatureExtractor proteinExtractor) {

        proteinExtractor.prepareProteinPrototypeForPockets()

        for (Pocket pocket in prediction.pockets) {
            FeatureExtractor extractor = proteinExtractor.createInstanceForPocket(pocket)

            double sum = 0
            double rawSum = 0

            for (Atom point in extractor.sampledPoints) {

                FeatureVector props = extractor.calcFeatureVector(point)

                double[] hist = getDistributionForPoint(classifier, props)
                double predictedScore = predictedScore(hist)   // not all classifiers give histogram that sums up to 1
                boolean predicted = predictedPositive(predictedScore)
                boolean observed = false

                if (collectingStatistics) {
                    double closestLigandDistance = ligandAtoms.count > 0 ? ligandAtoms.dist(point) : Double.MAX_VALUE
                    observed = (closestLigandDistance <= POSITIVE_POINT_LIGAND_DISTANCE)
                    stats.addPrediction(observed, predicted, predictedScore, hist)

                }
                if (collectPoints) {
                    labeledPoints.add(new LabeledPoint(point, hist, observed, predicted))
                }

                sum += pointScoreCalculator.transformedPointScore(hist)

                rawSum += predictedScore // ~ P(ligandable)
            }

            double score = sum
            pocket.newScore = score
            pocket.auxInfo.rawNewScore = rawSum / extractor.sampledPoints.count // ratio of predicted ligandable points
            pocket.auxInfo.samplePoints = extractor.sampledPoints.count
        }

    }

    private final double[] getDistributionForPoint(Classifier classifier, FeatureVector vect) {
        if (classifier instanceof FasterForest) {
            return ((FasterForest)classifier).distributionForAttributes(vect.array, 2)
        } else {
            PerfUtils.arrayCopy(vect.array, alloc)
            return classifier.distributionForInstance(auxInst)
        }

    }

    ClassifierStats getStats() {
        return stats
    }
}
