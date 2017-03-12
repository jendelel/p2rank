package cz.siret.prank.score.results

import groovy.transform.CompileStatic
import weka.classifiers.evaluation.NominalPrediction
import weka.classifiers.evaluation.Prediction
import weka.classifiers.evaluation.ThresholdCurve
import weka.core.Instances

/**
 * Helps to calculate Weka AUC and AUPRC
 *
 * note: calculation is extremely memory inefficient
 */
@CompileStatic
class WekaStatsHelper {

    List<ClassifierStats.Pred> preds
    Instances wekaPreds

    WekaStatsHelper(List<ClassifierStats.Pred> preds) {
        this.preds = preds

        wekaPreds = new ThresholdCurve().getCurve(toWekaNominalPredictions(preds), 1)
    }

    private ArrayList<Prediction> toWekaNominalPredictions(List<ClassifierStats.Pred> preds) {
        ArrayList<Prediction> res = new ArrayList<>(preds.size())
        for (ClassifierStats.Pred p : preds) {
            double actual = p.observed ? 1d : 0d
            double[] distribution = [1-p.score, p.score] as double[]
            res.add(new NominalPrediction(actual, distribution))
        }
        return res
    }

    /**
     *
     * see weka.classifiers.evaluation.Evaluation#areaUnderROC(int)
     *
     * @param preds
     * @return
     */
    double areaUnderROC() {
        return ThresholdCurve.getROCArea(wekaPreds);
    }

    /**
     *
     * see weka.classifiers.evaluation.Evaluation#areaUnderROC(int)
     *
     * @param preds
     * @return
     */
    double areaUnderPRC() {
        return ThresholdCurve.getPRCArea(wekaPreds);
    }




}
