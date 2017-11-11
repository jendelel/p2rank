package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.score.metrics.ClassifierStats
import cz.siret.prank.score.metrics.Curves
import cz.siret.prank.score.metrics.Histogram
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile
import static cz.siret.prank.utils.MathUtils.stddev

/**
 * results for eval-rescore, traineval and ploop routines
 */
@Slf4j
@CompileStatic
class EvalResults implements Parametrized, Writable  {

    /**
     * number of Eval runs this result represents
     */
    int runs = 0

    Evaluation eval
    Evaluation origEval                  // stores original results by other prediction method when rescoring
    ClassifierStats classifierStats
    ClassifierStats classifierTrainStats // classifier stats on train data

    Dataset.Result datasetResult

    Long trainTime
    Long evalTime

    int train_positives = 0
    int train_negatives = 0

    List<Double> featureImportances
    List<EvalResults> subResults = new ArrayList<>()

    Map<String, Double> additionalStats = new HashMap<>()

    boolean rescoring = !params.predictions  // new predictions vs. rescoring

    EvalResults(int runs) {
        this.runs = runs
        eval = new Evaluation()
        origEval = new Evaluation()
        classifierStats = new ClassifierStats()
        if (params.classifier_train_stats) {
            classifierTrainStats = new ClassifierStats()
        }
    }

    private static List<Double> repeat(Double value, int times) {
        List<Double> res = new ArrayList<>(times)
        for (int i=0; i!=times; i++)
            res.add(value)
        return res
    }

    private static List<Double> addVectors(List<Double> va, List<Double> vb) {
        if (va==null && vb==null) return null

        if (va==null) va = repeat(0d, vb.size())
        if (vb==null) vb = repeat(0d, va.size())

        List res = new ArrayList(va.size())
        for (int i=0; i!=va.size(); i++) {
            res.add ((double)va[i] + (double)vb[i])
        }

        return res
    }

    void addSubResults(EvalResults results) {
        subResults.add(results)

        eval.addAll(results.eval)
        origEval.addAll(results.origEval)
        classifierStats.addAll(results.classifierStats)
        if (classifierTrainStats!=null && results.classifierTrainStats!=null) {
            classifierTrainStats.addAll(results.classifierTrainStats)
        }

        // set only once to because of varoius caching
        if (trainTime==null) trainTime = results.trainTime
        if (evalTime==null) evalTime = results.evalTime

        train_negatives += results.train_negatives
        train_positives += results.train_positives

        featureImportances = addVectors(featureImportances, results.featureImportances)

        runs += results.runs
    }

    int getAvgTrainVectors() {
        avgTrainPositives + avgTrainNegatives
    }

    int getAvgTrainPositives() {
        (double)train_positives / runs
    }

    int getAvgTrainNegatives() {
        (double)train_negatives / runs
    }

    double getTrainPositivesRatio() {
        if (train_positives + train_negatives==0) return 0

        (double)train_positives / (train_positives + train_negatives)
    }

    double getTrainRatio() {
        if (train_negatives==0) return 1

        (double)train_positives / train_negatives
    }

    Map<String, Double> getStats() {
        Map<String, Double> m = eval.stats

        m.PROTEINS         = (double)m.PROTEINS         / runs
        m.POCKETS          = (double)m.POCKETS          / runs
        m.LIGANDS          = (double)m.LIGANDS          / runs
        m.LIGANDS_IGNORED  = (double)m.LIGANDS_IGNORED  / runs
        m.LIGANDS_SMALL    = (double)m.LIGANDS_SMALL    / runs
        m.LIGANDS_DISTANT  = (double)m.LIGANDS_DISTANT  / runs

        //===========================================================================================================//

        m.TIME_TRAIN_M = (double)(trainTime ?: 0) / 60000
        m.TIME_EVAL_M = (double)(evalTime ?: 0) / 60000
        m.TIME_M = m.TIME_TRAIN_M + m.TIME_EVAL_M

        m.TRAIN_VECTORS = avgTrainVectors
        m.TRAIN_POSITIVES = avgTrainPositives
        m.TRAIN_NEGATIVES = avgTrainNegatives
        m.TRAIN_RATIO = trainRatio
        m.TRAIN_POS_RATIO = trainPositivesRatio

        m.putAll classifierStats.metricsMap
        if (params.classifier_train_stats && classifierTrainStats!=null) {
            m.putAll classifierTrainStats.metricsMap.collectEntries { key, value -> ["train_" + key, value] } as Map<String, Double>
        }

        if (params.feature_importances && featureImportances!=null) {
            featureImportances = featureImportances.collect { (double)it/runs }.<Double>toList()
            getNamedImportances().each {
                m.put "_FI_"+it.name, it.importance
            }
        }

        m.putAll(additionalStats)

        return m
    }

    /**
     * Calculates sample standard deviation for all stats.
     * Only works for composite results (those that have subResults).
     */
    Map<String, Double> getStatsStddev() {
        assert !subResults.isEmpty()

        List<Map<String, Double>> subStats = subResults.collect { it.stats }.toList()

        Map res = new HashMap()
        for (String stat : subStats.head().keySet()) {
            double val = stddev subStats.collect { it.get(stat) }
            res.put(stat, val)
        }
        res
    }

    String statsCSV(Map stats) {
        stats.collect { "$it.key, ${Formatter.fmt(it.value)}" }.join("\n")
    }


    void logClassifierStats(ClassifierStats cs, String outdir) {
        String dir = "$outdir/classifier"
        mkdirs(dir)

        cs.histograms.properties.findAll { it.value instanceof Histogram }.each {
            String label = it.key
            Histogram hist = (Histogram) it.value

            writeFile "$dir/hist_${label}.csv", hist.toCSV()
        }

        if (cs.collecting && params.stats_curves)
            writeFile "$dir/roc_curve.csv", Curves.roc(cs.predictions).toCSV()
    }

    /**
     *
     * @param outdir
     * @param classifierName
     * @param summaryStats  in summary stats (like over multiple seed runs) we dont want all pocket details multiple times
     */
    void logAndStore(String outdir, String classifierName, Boolean logIndividualCases=null) {

        if (logIndividualCases==null) {
            logIndividualCases = params.log_cases
        }

        mkdirs(outdir)

        List<Integer> tolerances = params.eval_tolerances

        String succ_rates          = origEval.toSuccRatesCSV(tolerances)
        String succ_rates_rescored = eval.toSuccRatesCSV(tolerances)  // P2RANK predictions are in eval
        String succ_rates_diff     = eval.diffSuccRatesCSV(tolerances, origEval)
        String classifier_stats    = classifierStats.toCSV(" $classifierName ")

        writeFile "$outdir/success_rates.csv", succ_rates_rescored
        if (rescoring) {
            writeFile "$outdir/success_rates_original.csv", succ_rates
            writeFile "$outdir/success_rates_diff.csv", succ_rates_diff
        }
        writeFile "$outdir/classifier.csv", classifier_stats
        writeFile "$outdir/stats.csv", statsCSV(getStats())
        if (subResults.size() > 1) {
            writeFile "$outdir/stats_stddev.csv", statsCSV(getStatsStddev())
        }

        logClassifierStats(classifierStats, outdir)

        if (logIndividualCases) {
            origEval.sort()
            eval.sort()

            String casedir = "$outdir/cases"
            mkdirs(casedir)
            writeFile "$casedir/proteins.csv", eval.toProteinsCSV()
            writeFile "$casedir/ligands.csv", eval.toLigandsCSV()
            writeFile "$casedir/pockets.csv", eval.toPocketsCSV()
            writeFile "$casedir/ranks.csv", eval.toRanksCSV()
            if (rescoring) {
                writeFile "$casedir/ranks_original.csv", origEval.toRanksCSV()
            }
        }

        if (params.feature_importances && featureImportances!=null) {
            List<FeatureImportance> namedImportances = getNamedImportances()
            namedImportances.sort { -it.importance } // descending
            String sortedCsv = namedImportances.collect { it.name + ", " + PerfUtils.formatDouble(it.importance) }.join("\n") + "\n"
            writeFile("$outdir/feature_importances_sorted.csv", sortedCsv)
        }

        log.info "\n" + CSV.tabulate(classifier_stats) + "\n\n"
        if (rescoring) {
            log.info "\nSucess Rates - Original:\n" + CSV.tabulate(succ_rates) + "\n"
        }
        write "\nSucess Rates:\n" + CSV.tabulate(succ_rates_rescored) + "\n"
        if (rescoring) {
            log.info "\nSucess Rates - Diff:\n" + CSV.tabulate(succ_rates_diff) + "\n\n"
        }
    }

    List<FeatureImportance> getNamedImportances() {
        if (featureImportances==null) return null

        List<String> names = FeatureExtractor.createFactory().vectorHeader
        List<FeatureImportance> namedImportances = new ArrayList<>()

        for (int i=0; i!=names.size(); ++i) {
            namedImportances.add new FeatureImportance( names[i] , featureImportances[i])
        }
     
        return namedImportances
    }

    static class FeatureImportance {
        String name
        double importance

        FeatureImportance(String name, double importance) {
            this.name = name
            this.importance = importance
        }
    }

}