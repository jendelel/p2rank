package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PDBUtils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * Ligand mado of one or several pdb groups
 */
@Slf4j
class Ligand implements Parametrized {

    /**
     * pdb group name
     */
    String name

    /**
     * group id (resCode in pdb)
     */
    String code
    Atoms atoms
    Protein protein
    double contactDistance
    double centerToProteinDist

    /**
     * SAS points induced by the ligand
     */
    Atoms sasPoints

    Pocket predictedPocket


    Ligand(Atoms ligAtoms, Protein protein) {
        assert !ligAtoms.empty , "Trying to create ligand with no atoms!"

        atoms = new Atoms(ligAtoms)
        this.protein = protein
        List<Group> groups = atoms.getDistinctGroups()
        Set<String> uniqueNames = (groups*.PDBName).toSet()
        this.name = uniqueNames.join("&")
        this.code = (groups*.residueNumber).join("&")

        for (Atom a : atoms) {
            PDBUtils.correctBioJavaElement(a)
        }

        if (log.debugEnabled) {
            groups.each { Group g ->
                log.debug "\tligand group: $g.PDBName [$g.residueNumber] atoms:" + Atoms.allFromGroup(g).count + " component: " + g.getChemComp()
            }
        }

        log.info this.toString()
    }

//    Atoms calcContactAtoms(Atoms proteinAtoms) {
//        return proteinAtoms.cutoffAtoms(atoms, params.ligand_protein_contact_distance)
//    }

    Atoms getSasPoints() {
        if (sasPoints==null) {
            sasPoints = protein.connollySurface.points.cutoffAtoms(this.atoms, params.ligand_induced_volume_cutoff)
        }
        return sasPoints
    }

    Atom getCentroid() {
        atoms.centerOfMass
        //atoms.geometricCenter
    }

    String getNameCode() {
        name + "_" + code
    }

    int getSize() {
        atoms.count
    }

    @Override
    public String toString() {
        return "ligand $name atoms:$atoms.count"
    }

}
