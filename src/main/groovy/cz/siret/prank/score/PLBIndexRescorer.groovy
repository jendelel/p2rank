package cz.siret.prank.score

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.tables.PropertyTable
import cz.siret.prank.utils.PDBUtils
import cz.siret.prank.utils.StrUtils
import cz.siret.prank.utils.futils

/**
 * implemention of PLB index from
 * Soga et al. 2007 "Use of Amino Acid Composition to Predict Ligand-Binding Sites"
 */
@Slf4j
@CompileStatic
class PLBIndexRescorer extends PocketRescorer {

    static final PropertyTable aaPropensitiesTable   = PropertyTable.parse(futils.readResource("/tables/aa-propensities.csv"))

    @Override
    void rescorePockets(Prediction prediction) {

        List<ExtPocket> extPockets = new ArrayList<>()

        double M = prediction.pocketCount

        for (Pocket pocket : prediction.pockets) {
            double plbi = 0

            if (params.plb_rescorer_atomic) {
                for (Atom a : pocket.surfaceAtoms) {
                    String aaCode = PDBUtils.getAtomResidueCode(a)
                    Double prop = aaPropensitiesTable.getValue(aaCode, "RAx")
                    if (prop!=null) {
                        plbi += prop
                    }
                }
            } else {
                // according to article
                for (Group g : pocket.surfaceAtoms.distinctGroups) {
                    String aaCode = PDBUtils.getResidueCode(g)
                    Double prop = aaPropensitiesTable.getValue(aaCode, "RAx")
                    if (prop!=null) {
                        plbi += prop
                    }
                }
            }


            ExtPocket extPocket = new ExtPocket(pocket: pocket, PLBi: plbi)
            extPocket.PLBi = plbi
            extPockets.add(extPocket)
        }

        double mu = ((double)(extPockets*.PLBi).sum(0)) / M

        double sig = 0
        for (ExtPocket p : extPockets) {
            double x = p.PLBi - mu
            sig += x*x
        }
        sig = Math.sqrt(sig/M)

        for (ExtPocket p : extPockets) {
            p.ZPLB = (p.PLBi - mu) / sig
            p.pocket.newScore = p.ZPLB

            log.info StrUtils.toStr(p)
        }

    }

    private static class ExtPocket {
        Pocket pocket
        double PLBi
        double ZPLB
    }

}
