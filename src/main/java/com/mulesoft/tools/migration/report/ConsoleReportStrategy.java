package com.mulesoft.tools.migration.report;

/**
 * Created by davidcisneros on 6/7/17.
 */
public class ConsoleReportStrategy implements ReportingStrategy {

    @Override
    public void log(String message, ReportCategory reportCategory) {
        System.out.println(reportCategory.getDescription() + " : " + message);
    }
}