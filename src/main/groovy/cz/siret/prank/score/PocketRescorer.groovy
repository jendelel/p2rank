package cz.siret.prank.score

import groovy.util.logging.Slf4j
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.ListUtils

@Slf4j
abstract class PocketRescorer implements Parametrized {

    /** optional - for evaluation statistics */
    Protein ligandedProtein
    Atoms ligandAtoms = null

    boolean getCollectingStatistics() {
        return ligandAtoms!=null
    }

    void collectStats(Protein ligandedProtein) {
        this.ligandedProtein = ligandedProtein
        if (ligandedProtein!=null) {
            ligandAtoms = ligandedProtein.allLigandAtoms
        }
    }

    /**
     * should set pocket.newScore on all pockets
     * and optionally store information to pocket.auxInfo
     */
    abstract void rescorePockets(Prediction prediction);

    void reorderPockets(Prediction prediction) {

        rescorePockets(prediction)

        if (!params.predictions) {
            prediction.reorderedPockets = new ArrayList<>(prediction.pockets)
            prediction.reorderedPockets = prediction.reorderedPockets.sort {
                Pocket a, Pocket b -> b.newScore <=> a.newScore
            } // descending

        }

        setNewRanks(prediction)
    }

    private void setNewRanks(Prediction prediction) {
        int i = 1
        for (Pocket pocket : prediction.reorderedPockets) {
            pocket.newRank = i++
        }
    }

    /**
     *
     * @param n reorder only first #true pockets + n
     */
    void reorderFirstNPockets(Prediction prediction, int n) {

        rescorePockets(prediction)

        log.info "reordering first $n of $prediction.pocketCount pockets"

        ArrayList<Pocket> head = ListUtils.head(n, prediction.pockets)
        ArrayList<Pocket> tail = ListUtils.tail(n, prediction.pockets)

        reorder(head)

        prediction.pockets = head + tail

        setNewRanks(prediction)
    }

    void reorder(ArrayList<Pocket> pockets) {
        pockets.sort(new Comparator<Pocket>() {
            int compare(Pocket o1, Pocket o2) {
                return Double.compare(o2.newScore, o1.newScore)
            }
        })
    }

}