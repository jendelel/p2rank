package cz.siret.prank.score.results

import cz.siret.prank.domain.Prediction
import cz.siret.prank.score.prediction.PrankPocket
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.PerfUtils

/**
 *
 */
class PredictionSummary {

    private Prediction prediction

    PredictionSummary(Prediction prediction) {
        this.prediction = prediction
    }

    CSV toCSV() {
        StringBuilder sb = new StringBuilder()

        sb << "name,rank,score,connolly_points,surf_atoms,center_x,center_y,center_z,residue_ids,surf_atom_ids   " << '\n'

        for (pp in prediction.reorderedPockets) {

            PrankPocket p = (PrankPocket) pp

            String fmtScore = PerfUtils.formatDouble(p.newScore)

            def x = PerfUtils.formatDouble(p.centroid.x)
            def y = PerfUtils.formatDouble(p.centroid.y)
            def z = PerfUtils.formatDouble(p.centroid.z)

            def surfAtomIds = p.surfaceAtoms*.PDBserial.join(" ")

            Set resIds = new TreeSet(p.surfaceAtoms.distinctGroups.collect { it.residueNumber.toString() })
            String strResIds = resIds.join(" ")

            sb << "$p.name,$p.newRank,$fmtScore,$p.innerPoints.count,$p.surfaceAtoms.count,$x,$y,$z,$strResIds,$surfAtomIds\n"
        }

        return new CSV(sb.toString())
    }

    String toTable() {
        return toCSV().tabulated(10,10,10,10,10)
    }

}
