package cz.siret.prank.domain

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import cz.siret.prank.geom.Atoms

@CompileStatic
abstract class Pocket {

    String name = "pocket"
    Atoms surfaceAtoms = new Atoms()
    Atom centroid

    /**
     * original rank of predicted pocket, starting with 1
     */
    int rank

    /**
     * rank of pocket after rescoring
     */
    int newRank
    double newScore

    PocketStats stats = new PocketStats()
    AuxInfo auxInfo = new AuxInfo()
    Map<String, Object> cache = new HashMap<>() // cache for various data

    static class AuxInfo {
        int samplePoints
        double rawNewScore
    }

    @Override
    String toString() {
        return "pocket rank:$rank surfaceAtoms:${surfaceAtoms.count}"
    }

    static class PocketStats {

        double pocketScore
        double realVolumeApprox

    }

}
