package cz.siret.prank.collectors

import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.chemproperties.ChemFeatureExtractor
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.score.criteria.IdentificationCriterium
import cz.siret.prank.utils.ListUtils

/**
 * extracts vectors for sampled points in predicted pockets
 * judging correct by distance to the closest ligand atom
 */
@Slf4j
class PointVectorCollector extends VectorCollector implements Parametrized {

    /** distance from the point to the ligand that identifies positive point */
    final double POSITIVE_VC_LIGAND_DISTANCE = params.positive_point_ligand_distance
    final double NEGATIVES_DIST = params.positive_point_ligand_distance + params.neutral_points_margin

    FeatureExtractor extractorFactory
    IdentificationCriterium criterion

    PointVectorCollector(FeatureExtractor extractorFactory, IdentificationCriterium criterion) {
        this.criterion = criterion
        this.extractorFactory = extractorFactory
    }

    Atoms getTrainingRelevantLigandAtoms(PredictionPair pair) {
        Atoms res = new Atoms()

        if (params.positive_def_ligtypes.contains("relevant")) res.addAll( pair.liganatedProtein.ligands*.atoms )
        if (params.positive_def_ligtypes.contains("ignored")) res.addAll( pair.liganatedProtein.ignoredLigands*.atoms )
        if (params.positive_def_ligtypes.contains("small")) res.addAll( pair.liganatedProtein.smallLigands*.atoms )
        if (params.positive_def_ligtypes.contains("distant")) res.addAll( pair.liganatedProtein.distantLigands*.atoms )

        return res
    }

    @Override
    Result collectVectors(PredictionPair pair) {
        Result finalRes = new Result()

        FeatureExtractor proteinExtractorPrototype = extractorFactory.createPrototypeForProtein(pair.prediction.protein)

        Atoms ligandAtoms = getTrainingRelevantLigandAtoms(pair) //pair.liganatedProtein.allLigandAtoms.withKdTreeConditional()


        if (ligandAtoms.empty) {
            log.error "no ligands! [{}]", pair.liganatedProtein.name
        }

        if (params.train_all_surface) {

            finalRes = collectWholeSurface(ligandAtoms, proteinExtractorPrototype)

        } else {

            List<Pocket> usePockets = pair.prediction.pockets  // use all pockets

            if (params.train_pockets>0) {
                usePockets = [ *pair.getCorrectlyPredictedPockets(criterion) , *ListUtils.head(params.train_pockets, pair.getFalsePositivePockets(criterion)) ]
            }

            for (Pocket pocket in usePockets) {
                try {
                    FeatureExtractor pocketExtractor = proteinExtractorPrototype.createInstanceForPocket(pocket)
                    Result pocketRes = collectForPocket(pocket, pair, ligandAtoms, pocketExtractor)
                    //synchronized (finalRes) {
                    finalRes.addAll(pocketRes)
                    //}
                } catch (Exception e) {
                    log.error("skipping extraction from pocket:$pocket.name reason: " + e.getMessage(), e)
                }
            }
        }

        return finalRes
    }

    Result collectWholeSurface(Atoms ligandAtoms, FeatureExtractor proteinExtractorPrototype) {

        FeatureExtractor proteinExtractor = (proteinExtractorPrototype as ChemFeatureExtractor).createInstanceForWholeProtein()
        Result res = new Result()

        Atoms points = proteinExtractor.sampledPoints

        if (params.train_lig_cutoff > 0) {
            points = points.cutoffAtoms(ligandAtoms, params.train_lig_cutoff)
        }

        for (Atom point in points) {        // TODO lot of repeated code with next method... refactor!

            try {
                double closestLigandDistance = ligandAtoms.dist(point)
                boolean ligPoint = (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE)
                boolean negPoint = (closestLigandDistance >= NEGATIVES_DIST)
                // points in between are left out from training

                if (ligPoint || negPoint) {
                    int clazz = ligPoint ? 1 : 0

                    FeatureVector prop = proteinExtractor.calcFeatureVector(point)

                    List<Double> vect = prop.getVector()
                    vect.add((double)clazz)
                    res.add(vect)

                    if (ligPoint) {
                        res.positives++
                    } else {
                        res.negatives++
                    }
                }

            } catch (Exception e) {
                log.error("skipping extraction for point, reason: " + e.getMessage(), e)
            }
        }

        return res
    }

    private Result collectForPocket(Pocket pocket, PredictionPair pair, Atoms ligandAtoms, FeatureExtractor pocketExtractor) {
        boolean ligPocket = pair.isCorrectlyPredictedPocket(pocket, criterion)

        Result res = new Result()

        for (Atom point in pocketExtractor.sampledPoints) {

            double closestLigandDistance = ligandAtoms.dist(point)
            if (closestLigandDistance > 100) closestLigandDistance = 100

            boolean ligPoint = (closestLigandDistance <= POSITIVE_VC_LIGAND_DISTANCE)

            boolean includePoint = false
            int clazz = ligPoint ? 1 : 0

            if (ligPoint) {
                res.positives++
                includePoint = true
            //} else {
            } else if (!ligPocket && !ligPoint && closestLigandDistance > NEGATIVES_DIST) {  // GAP ... helps
                // so we are skipping points in gap and negative points in positive pockets
                res.negatives++
                includePoint = true
            }

            if (includePoint) {
                FeatureVector prop = pocketExtractor.calcFeatureVector(point)

                List<Double> vect = prop.getVector()
                vect.add((double)clazz)
                res.add(vect)

                //log.trace "TRAIN VECT: " + vect
            }

        }

        return res
    }

    @Override
    List<String> getHeader() {
        return extractorFactory.vectorHeader + "is_liganated_point"
    }

}
