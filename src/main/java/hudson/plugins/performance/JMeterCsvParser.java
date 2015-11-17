package hudson.plugins.performance;

import hudson.Extension;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.xml.sax.SAXException;

public class JMeterCsvParser extends AbstractParser {

  public final boolean skipFirstLine;
  public final String delimiter;
  public int timestampIdx = -1;
  public int elapsedIdx = -1;
  public int responseCodeIdx = -1;
  public int successIdx = -1;
  public int urlIdx = -1;
  public final String pattern;

  @DataBoundConstructor
  public JMeterCsvParser(String glob, String pattern, String delimiter, Boolean skipFirstLine) throws Exception {
    super(glob);
    this.skipFirstLine = skipFirstLine;
    this.delimiter = delimiter;
    this.pattern = pattern;
    String[] fields = pattern.split(delimiter);
    for (int i = 0; i < fields.length; i++) {
      String field = fields[i];
      if ("timeStamp".equals(field)) {
        timestampIdx = i;
      } else if ("elapsed".equals(field)) {
        elapsedIdx = i;
      } else if ("responseCode".equals(field)) {
        responseCodeIdx = i;
      } else if ("success".equals(field)) {
        successIdx = i;
      } else if ("URL".equals(field)) {
        urlIdx = i;
      }
    }
    if (timestampIdx < 0 || elapsedIdx < 0 || responseCodeIdx < 0
        || successIdx < 0 || urlIdx < 0) {
      throw new Exception("Missing required column");
    }
  }

  @Extension
  public static class DescriptorImpl extends PerformanceReportParserDescriptor {
    @Override
    public String getDisplayName() {
      return "JMeterCSV";
    }

    public FormValidation doCheckDelimiter(@QueryParameter String delimiter) {
      if (delimiter == null || delimiter.isEmpty()) {
        return FormValidation.error(Messages
            .CsvParser_validation_delimiterEmpty());
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckPattern(@QueryParameter String pattern) {
      if (pattern == null || pattern.isEmpty()) {
        FormValidation.error(Messages.CsvParser_validation_patternEmpty());
      }
      Set<String> missing = new HashSet<String>();
      validatePresent(missing, pattern, "timeStamp");
      validatePresent(missing, pattern, "elapsed");
      validatePresent(missing, pattern, "responseCode");
      validatePresent(missing, pattern, "success");
      validatePresent(missing, pattern, "URL");
      if (missing.isEmpty()) {
        return FormValidation.ok();
      } else {
        StringBuilder builder = new StringBuilder();
        for (String field : missing) {
          builder.append(field + ", ");
        }
        builder.setLength(builder.length() - 2);
        return FormValidation.error(Messages
            .CsvParser_validation_MissingFields() + ": " + builder.toString());
      }
    }

    private void validatePresent(Set<String> missing, String pattern,
        String string) {
      if (!pattern.contains(string)) {
        missing.add(string);
      }
    }
  }

  @Override
  public String getDefaultGlobPattern() {
    return "**/*.csv";
  }

  // This may be unnecessary. I tried many things getting the pattern to show up
  // correctly in the UI and this was one of them.
  public String getDefaultPattern() {
    return "timeStamp,elapsed,URL,responseCode,responseMessage,threadName,success,bytes,allThreads,Latency,Connect";
  }

  @Override
  PerformanceReport parse(File reportFile) throws Exception {
    final PerformanceReport report = new PerformanceReport();
    report.setReportFileName(reportFile.getName());

    final BufferedReader reader = new BufferedReader(new FileReader(reportFile));
    try {
      String line = reader.readLine();
      if (line != null && skipFirstLine) {
        line = reader.readLine();
      }
      while (line != null) {
        final HttpSample sample = getSample(line);
        if (sample != null) {
          try {
            report.addSample(sample);
          } catch (SAXException e) {
            throw new RuntimeException("Error parsing file '"+ reportFile +"': Unable to add sample for line " + line, e);
          }
        }
        line = reader.readLine();
      }

      return report;
    } finally {
      if (reader != null) {
        reader.close();
      }
    }
  }

  /**
   * Parses a single HttpSample instance from a single CSV line.
   *
   * @param line
   *          file line with the provided pattern (cannot be null).
   * @return An sample instance (never null).
   */
  private HttpSample getSample(String line) {
    final HttpSample sample = new HttpSample();
    final String commasNotInsideQuotes = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)";
    final String[] values = line.split(commasNotInsideQuotes);
    sample.setDate(new Date(Long.valueOf(values[timestampIdx])));
    sample.setDuration(Long.valueOf(values[elapsedIdx]));
    sample.setHttpCode(values[responseCodeIdx]);
    sample.setSuccessful(new Boolean(Integer.valueOf(values[successIdx])==1));
    sample.setUri(values[urlIdx]);
    return sample;
  }
}
