package cz.siret.prank.program

import cz.siret.prank.program.routines.CrossValidation
import cz.siret.prank.program.routines.EvaluateRoutine
import cz.siret.prank.program.routines.Experiments
import cz.siret.prank.program.routines.PredictRoutine
import cz.siret.prank.program.routines.RescoreRoutine
import cz.siret.prank.program.routines.SeedLoop
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.StrUtils
import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.futils
import cz.siret.prank.utils.CmdLineArgs

@Slf4j
class Main implements Parametrized, Writable {

    static Properties buildProperties = futils.loadProperties('/build.properties')

    CmdLineArgs args
    String command
    String installDir

    boolean error = false

//===========================================================================================================//

    String getInstallDir() {
        return installDir
    }

    String getConfigFileParam() {
        args.get('config','c')
    }

    void initParams(Params params, String defaultConfigFile) {

        log.info "loading default config from [${futils.absPath(defaultConfigFile)}]"
        File defaultParams = new File(defaultConfigFile)
        ConfigLoader.overrideConfig(params, defaultParams)
        String configParam = configFileParam

        // TODO allow multiple -c params override default+dev+working
        if (configParam!=null) {

            if (!configParam.endsWith(".groovy") && futils.exists(configParam+".groovy"))  {
                configParam = configParam + ".groovy"
            }

            File customParams = new File(configParam)
            if (!customParams.exists()) {
                customParams = new File("$installDir/config/$configParam")
            }

            log.info "overriding default config with [${futils.absPath(customParams.path)}]"
            ConfigLoader.overrideConfig(params, customParams)
        }

        params.updateFromCommandLine(args)
        params.with {
            dataset_base_dir = evalDirParam(dataset_base_dir)
            output_base_dir = evalDirParam(output_base_dir)
        }

        String mod = args.get('m')
        if (mod!=null) {
            params.model = mod
        }

        log.debug "CMD LINE ARGS: " + args
    }

    String evalDirParam(String dir) {
        if (dir==null) {
            dir = "."
        } else {
            if (!new File(dir).isAbsolute()) {
                dir = "$installDir/$dir"
            }
        }
        assert dir != null
        dir = futils.normalize(dir)
        assert dir != null
        return dir
    }

    String findModel() {
        String modelName = params.model

        String modelf = modelName
        if (!futils.exists(modelf)) {
            modelf = "$installDir/models/$modelf"
        }
        if (!futils.exists(modelf)) {
            log.error "Model file [$modelName] not found!"
            throw new PrankException("model not found")
        }
        return modelf
    }

    static String findDataset(String dataf) {
        if (dataf==null) {
            throw new PrankException('dataset not specified!')
        }

        if (!futils.exists(dataf)) {
            log.info "looking for dataset in working dir [${futils.absPath(dataf)}]... failed"
            dataf = "${Params.inst.dataset_base_dir}/$dataf"
        }
        log.info "looking for dataset in dataset_base_dir [${futils.absPath(dataf)}]..."
        return dataf
    }

    /**
     * -o makes outdir in wrking path
     * -l makes outdir in output_base_dir
     * @param defaultName of dir created in output_base_dir
     */
    String findOutdir(String defaultName) {
        String outdir = null
        String label = args.get('label','l')

        String prefixdate = ""
        if (params.out_prefix_date) {
            prefixdate = StrUtils.timeLabel() + "_"
        }

        if (label==null) {
            label = args.get('model','m')  // use model name as label
        }

        String base = params.output_base_dir
        if (StringUtils.isNotEmpty(params.out_subdir)) {
            base += "/" + params.out_subdir
        }

        if (label!=null) {
            outdir = "${base}/${prefixdate}${defaultName}_$label"
        } else {
            outdir = args.get('o')
            if (outdir==null) {
                outdir = "${base}/${prefixdate}$defaultName"
            }
        }

        futils.mkdirs(outdir)
        return outdir
    }

    Dataset loadDataset() {
        Dataset.loadFromFile(findDataset(args.unnamedArgs[0])) // by default dataset is the first unnamed argument adter command
    }

    Dataset loadDatasetOrFile() {
        String fparam = args.namedArgMap.get("f")  // single file param -f
        if (fparam!=null) {
            return Dataset.createSingleFileDataset(fparam)
        } else {
            return loadDataset()
        }
    }

    String findInstallDir() {

        String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = URLDecoder.decode(path, "UTF-8");

        return futils.normalize(futils.dir(decodedPath) + "/../")

    }

//===========================================================================================================//

    void doRunPredict(String label, boolean evalPredict) {
        initParams(params, "$installDir/config/default-predict.groovy")

        Dataset dataset = loadDatasetOrFile()

        PredictRoutine predictRoutine = new PredictRoutine(
                dataset,
                findModel(),
                findOutdir("${label}_$dataset.label"))

        if (evalPredict) {
            predictRoutine.collectStats = true
        }

        Dataset.Result result = predictRoutine.execute()
        finalizeDatasetResult(result)
    }

    void finalizeDatasetResult(Dataset.Result result) {
        if (result.hasErrors()) {
            error = true
            write "ERROR on processing $result.errorCount file(s):"

            for (Dataset.Item item : result.errorItems) {
                write "    [$item.label]"
            }
        }
    }

//===========================================================================================================//

    void runPredict() {
        doRunPredict("predict", false)
    }

    void runEvalPredict() {
        doRunPredict("eval_predict", true)
    }

    void runRescore() {

        Dataset dataset = loadDatasetOrFile()

        Dataset.Result result = new RescoreRoutine(
                dataset,
                findModel(),
                findOutdir("rescore_$dataset.label")).execute()

        finalizeDatasetResult(result)
    }

    void runEvalRescore() {

        Dataset dataset = loadDataset()

        new EvaluateRoutine(
                dataset,
                findModel(),
                findOutdir("eval_rescore_$dataset.label")).execute()

    }

    private runExperiment(String routineName) {

        initPredictDefaultParams()

        new Experiments(args, this).execute(routineName)
    }

    private runCrossvalidation() {

        initPredictDefaultParams()

        Dataset dataset = loadDataset()
        String outdir = findOutdir("crossval_" + dataset.label)

        futils.overwrite("$outdir/params.txt", params.toString())

        CrossValidation routine = new CrossValidation(outdir, dataset)
        new SeedLoop(routine, outdir).execute()
    }

    void runHelp() {
        println futils.readResource('/help.txt')
    }

    void initPredictDefaultParams() {
        initParams(params, "$installDir/config/default-predict.groovy")
    }

    /**
     * @return false if successful, true it there was some (recoverable) error during execution
     */
    boolean run() {
        command = args.unnamedArgs.size()>0 ? args.unnamedArgs.first() : "help"
        args.shiftUnnamedArgs()

        installDir = findInstallDir()

        if (command=="ploop") {
            args.hasRangedParams = true
        }

        if (command=='help' || args.hasSwitch('h','help')) {
            runHelp()
            return true
        }

        initParams(params, "$installDir/config/default.groovy")

        switch (command) {
            case 'predict':       runPredict()
                break
            case 'eval-predict':  runEvalPredict()
                break
            case 'rescore':       runRescore()
                break
            case 'eval-rescore':  runEvalRescore()
                break
            case 'crossval':      runCrossvalidation()
                break
            case 'run':           runExperiment(args.unnamedArgs[0])
                break
            default:
                runExperiment(command)
        }

        return error
    }



//===========================================================================================================//

    Main(CmdLineArgs args) {
        this.args = args
    }

    static String getVersion() {
        return buildProperties.getProperty('version')
    }

    static String getVersionName() {
        return "P2RANK $version"
    }


    static void main(String[] args) {
        ATimer timer = ATimer.start()

        CmdLineArgs parsedArgs = CmdLineArgs.parse(args)

        if (parsedArgs.hasSwitch("v", "version")) {
            write "$versionName"
            return
        }


        write "----------------------------------------------------------------------------------------------"
        write " $versionName"
        write "----------------------------------------------------------------------------------------------"
        write ""

        boolean error = false

        try {

            error = new Main(parsedArgs).run()

        } catch (PrankException e) {

            error = true
            writeError e.getMessage()

        } catch (Exception e) {

            error = true
            writeError e.getMessage(), e // on unknown exception also print stack trace

        }

        write ""
        write "----------------------------------------------------------------------------------------------"
        write " finished ${error?"with ERROR":"successfully"} in $timer.formatted"
        write "----------------------------------------------------------------------------------------------"

        if (error) {
            System.exit(1)
        }

        //CutoffAtomsCallLog.INST.printOut("local-cutoffAtomsAround");

    }

}
