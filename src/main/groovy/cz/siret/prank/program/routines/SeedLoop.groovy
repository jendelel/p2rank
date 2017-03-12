package cz.siret.prank.program.routines

import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j

/**
 * Routine that iterates through different values of random seed param
 */
@Slf4j
class SeedLoop extends CompositeRoutine {

    CompositeRoutine routine  // routine to iterate on

    SeedLoop(CompositeRoutine routine, String outdir) {
        this.routine = routine
        this.outdir = outdir
    }

    @Override
    Results execute() {
        def timer = ATimer.start()

        Results results = new Results(0)

        int origSeed = params.seed
        int n = params.loop
        for (int seedi in 1..n) {
            write "random seed iteration: $seedi/$n"

            String label = "seed.${params.seed}"
            routine.outdir = "$outdir/$label"

            results.addAll(routine.execute())

            params.seed += 1
        }

        results.logAndStore(outdir, params.classifier)
        if (routine instanceof CrossValidation) {
            CrossValidation cv = (CrossValidation) routine
            logSummaryResults(cv.dataset.label, "crossvalidation", results)
        } else {
            logSummaryResults("--", "evaluation", results)
        }
        params.seed = origSeed // set seed back for other experiments

        logTime "random seed iteration finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"


        return results
    }

}
