package cz.siret.prank.utils

import com.google.common.base.Splitter
import groovy.transform.CompileStatic
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

import java.text.DateFormat
import java.text.SimpleDateFormat

@CompileStatic
class StrUtils {

    static DateFormat DATE_LABEL_FORMAT = new SimpleDateFormat("yyyy.MM.dd_HHmm")

    private static class MSN extends ToStringStyle {
        MSN() {
            super()

            this.setContentStart("[");
            this.setFieldSeparator(SystemUtils.LINE_SEPARATOR + "  ");
            this.setFieldSeparatorAtStart(true);
            this.setContentEnd(SystemUtils.LINE_SEPARATOR + "]");

            useShortClassName = true
            useIdentityHashCode = false
        }
    }

    public static final ToStringStyle MULTILINE_SIMPLE_NAMES = new MSN()


    static String toStr(Object obj) {

        return ToStringBuilder.reflectionToString(obj, MULTILINE_SIMPLE_NAMES)
    }

    static prefixLines(String prefix, String text) {
        prefix + text.readLines().join("\n"+prefix)
    }


    static String timeLabel() {
        DATE_LABEL_FORMAT.format(new Date())
    }

    static List<String> split(String str, String splitter) {
        Splitter.on(splitter).omitEmptyStrings().trimResults().split(str).toList()
    }

    static List<String> split(String str) {
        split(str, " ")
    }

    /**
     *
     * @param liststr format: [a, b,c]
     * @return
     */
    static List<String> parseList(String liststr) {
        assert liststr!=null
        assert liststr.length()>=2

        String splitter = ","
        if (!liststr.contains(splitter)) {
            splitter = "_"
        }

        liststr = liststr.substring(1, liststr.length()-1)

        return split(liststr, splitter)
    }

}
