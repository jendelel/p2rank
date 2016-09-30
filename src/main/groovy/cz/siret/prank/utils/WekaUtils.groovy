package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier
import weka.core.*
import weka.core.converters.ArffSaver
import weka.core.converters.ConverterUtils
import weka.filters.Filter
import weka.filters.unsupervised.attribute.NumericToNominal
import weka.filters.unsupervised.instance.Randomize
import weka.filters.unsupervised.instance.RemovePercentage

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Slf4j
@CompileStatic
class WekaUtils implements Writable {

    private static final int BUFFER_SIZE = 10 * 1024 * 1024;

    static Instances createDatasetWithBinaryClass(List<String> header, int initialCapacity = 10) {

        ArrayList<Attribute> attInfo = new ArrayList<>()
        int i = 0
        attInfo.addAll(header.collect {
            new Attribute(it, i++)
        })

        attInfo.add(new Attribute("@@class@@", ["0", "1"], new ProtectedProperties(new Properties())))

        Instances auxWekaDataset = new Instances("TestInstances", attInfo, initialCapacity);
        auxWekaDataset.setClassIndex(auxWekaDataset.numAttributes() - 1)

        //log.debug "NUM CLASSES: " + auxWekaDataset.numClasses()

        return auxWekaDataset
    }

    static Instance toInstance(List<Double> vector) {
        return new DenseInstance(1, PerfUtils.toPrimitiveArray(vector))
    }

    // == classifiers ===

    static void saveClassifier(Classifier classifier, String fileName) {

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), BUFFER_SIZE))
        //zos.setLevel(9)

        ZipEntry entry = new ZipEntry("weka.model")
        zos.putNextEntry(entry)

        def oos = new ObjectOutputStream(new BufferedOutputStream(zos, BUFFER_SIZE))
        oos.writeObject(classifier)
        oos.flush()

        //SerializationHelper.write(zos, classifier)

        zos.closeEntry()

        oos.close()
    }

    static Classifier loadClassifier(String fileName) {
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileName), BUFFER_SIZE))

            zis.getNextEntry()

            return (Classifier) SerializationHelper.read(zis)
        } catch (FileNotFoundException e) {
            log.error "model file doesn't exist! ($fileName)"
        }
    }

    /**
     * load from jar
     */
    static Classifier loadClassifierFromPath(String path) {
        return (Classifier) SerializationHelper.read(path.class.getResourceAsStream(path));
    }

    static void trainClassifier(Classifier classifier, Instances data) {
        classifier.buildClassifier(data)
    }

    // == data ===

    static Instances loadData(String fileName) {
        ConverterUtils.DataSource source = new ConverterUtils.DataSource(fileName)
        Instances data = source.getDataSet();

        // setting class attribute if the data format does not provide this information
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        return data
    }

    static void saveDataArff(String fileName, boolean compressed, Instances data) {
        File arff = new File(fileName)

        if (arff.exists())
            arff.delete()


        ArffSaver saver = new ArffSaver();
        saver.setCompressOutput(compressed)

        saver.setDestination(new BufferedOutputStream(new FileOutputStream(arff), BUFFER_SIZE))
        //saver.setFile(arff);

        saver.setInstances(data);
        saver.writeBatch();
    }


    static Instances joinInstances(List<Instances> instList) {
        assert !instList.empty

        Instances res = new Instances(instList.head())

        for (Instances inst : instList.tail()) {
            res.addAll(inst)
        }

        return res
    }

    static List<Instances> splitPositivesNegatives(Instances all) {
        Instances pos = new Instances(all, 0)
        Instances neg = new Instances(all, 0)

        for (Instance inst : all) {
            if (inst.classValue() == 0) {
                neg.add(inst)
            } else {
                pos.add(inst)
            }
        }

        return [pos, neg]
    }

    static int countClass(Instances data, double val) {
        int sum = 0
        for (Instance inst : data) {
            if (inst.classValue() == val) {
                sum++
            }
        }
        sum
    }

    static int countPositives(Instances data) {
        countClass(data, 1d)
    }

    static int countNegatives(Instances data) {
        countClass(data, 0d)
    }

    // == filters ===

    static Instances subsample(double keepPercentage, int seed, Instances data) {
        assert keepPercentage>=0 && keepPercentage<=1

        return removePercentage( randomize(data, seed), 1-keepPercentage)

    }

    /**
     *
     * @param pc
     * @param data
     * @return
     */
    static Instances removePercentage(Instances data, double pc) {
        int keepCount = (int)Math.round(data.size() * (1d-pc))
        Instances result = new Instances(data, 0, keepCount)
        return result
    }

    static Instances randomize(Instances data, int seed) {
        Randomize filter = new Randomize()
        filter.setInputFormat(data)
        filter.setRandomSeed(seed)
        return Filter.useFilter(data, filter)
    }

    static Instances numericToNominal(String attributeIndices, Instances data) {

        NumericToNominal filter = new NumericToNominal()
        filter.setInputFormat(data)
        filter.setAttributeIndices(attributeIndices)
        return Filter.useFilter(data, filter)
    }

}
