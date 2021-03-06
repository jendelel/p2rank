package cz.siret.prank.geom.samplers

import groovy.transform.CompileStatic
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.FPockeLoader
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params

@CompileStatic
class SurfacePointSampler extends PointSampler implements Parametrized {

    public final double VAN_DER_WAALS_COMPENSATION = params.surface_additional_cutoff

    SurfacePointSampler(Protein protein) {
        super(protein)
     }

    @Override
    Atoms samplePointsForPocket(Pocket pocket) {

        String cacheKey = "sampled-points"
        if (train)
            cacheKey += "-train"

        if (pocket.cache.containsKey(cacheKey)) {
            return (Atoms) pocket.cache.get(cacheKey)
        }

        Atoms protSurf = protein.getSurface(train).points


        Atoms res
        if (Params.inst.strict_inner_points && (pocket instanceof FPockeLoader.FPocketPocket)) {
            FPockeLoader.FPocketPocket fpocket = (FPockeLoader.FPocketPocket) pocket
            res = protSurf.cutoffAtoms(fpocket.vornoiCenters, 6) // 6 is max radius of fpocket alpha sphere
        } else {

            res = protSurf.cutoffAtoms(pocket.surfaceAtoms, protein.connollySurface.solventRadius + VAN_DER_WAALS_COMPENSATION)
        }

        pocket.cache.put(cacheKey, res)

        return res
    }

}
