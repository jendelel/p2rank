package cz.siret.prank.collectors

import groovy.transform.CompileStatic
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureVector

@CompileStatic
abstract class VectorCollector {

    abstract Result collectVectors(PredictionPair pair);

    abstract List<String> getHeader();

    String getHeaderCSV() {
        return getHeader().join(",")
    }

    static final class Result {
        int positives = 0
        int negatives = 0
        List<FeatureVector> vectors = new ArrayList<>()

        void add(List<Double> vect) {
            vectors.add(new DoubleVector(vect))
        }

        void addAll(Result r) {
            positives += r.positives
            negatives += r.negatives
            vectors.addAll(r.vectors)
        }

        String toCSV() {
            StringBuilder sb = new StringBuilder()
            for (FeatureVector v in vectors) {
                sb.append(v.toCSV())
                sb.append("\n")
            }
            return sb.toString()
        }
    }

}
