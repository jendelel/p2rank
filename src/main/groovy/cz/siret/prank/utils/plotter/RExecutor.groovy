package cz.siret.prank.utils.plotter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import cz.siret.prank.utils.futils


@CompileStatic
@Slf4j
class RExecutor {

    String rpath = ""
    String rbinary = "Rscript"

    String getRCommand() {
        String command = ""
        if (rpath!="") {
            command += rpath + "/"
        }
        command += rbinary
        command
    }

    int runScript(String scriptFile, String dir=null) {

        log.info "executing R script [{$scriptFile}]"

        if (dir==null) {
            dir = futils.dir(scriptFile)
        }

        futils.mkdirs(dir)

        def command = "$RCommand \"$scriptFile\"" // Create the String
        Process proc = command.execute((List)null, new File(dir))
        proc.waitFor()

        StringBuilder out = new StringBuilder("")
        StringBuilder err = new StringBuilder("")
        proc.waitForProcessOutput(out, err)


        int exitcode = proc.exitValue()

        log.info "R exit code: $exitcode"
        if (out)
            log.info "R stdout:\n{}", out
        if (err)
            log.info "R stderr:\n{}", err

        return exitcode
    }

    /**
     *
     * @param outdir execute in this directory, location of output files
     * @return
     */
    int runCode(String code, String name, String outdir, String scriptDir=null) {

        if (scriptDir==null) {
            scriptDir = "$outdir/rcode"
        }

        String scriptf = "$scriptDir/${name}.R"
        scriptf = futils.absPath(scriptf)
        futils.overwrite scriptf, code

        return runScript(scriptf, outdir)
    }

}
