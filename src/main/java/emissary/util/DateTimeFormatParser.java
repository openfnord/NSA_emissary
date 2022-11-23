package emissary.util;

import emissary.config.Configurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DateTimeFormatParser {

    protected static final List<DateTimeFormatter> dateTimeZoneFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeOffsetFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateTimeFormats = new ArrayList<>();
    protected static final List<DateTimeFormatter> dateFormats = new ArrayList<>();

    private static final String DATE_TIME_ZONE_FORMAT = "DATE_TIME_ZONE_FORMAT";
    private static final String DATE_TIME_OFFSET_FORMAT = "DATE_TIME_OFFSET_FORMAT";
    private static final String DATE_TIME_FORMAT = "DATE_TIME_FORMAT";
    private static final String DATE_FORMAT = "DATE_FORMAT";

    private static final ZoneId GMT = ZoneId.of("GMT");

    protected static final Logger logger = LoggerFactory.getLogger(DateTimeFormatParser.class);

    static {
        configure();
    }

    private DateTimeFormatParser() {}

    protected static void configure() {

        Configurator configG;
        try {
            configG = emissary.config.ConfigUtil.getConfigInfo(DateTimeFormatParser.class);
        } catch (IOException e) {
            logger.error("Cannot open default config file", e);
            return;
        }

        loadDateTimeEntries(configG, DATE_TIME_ZONE_FORMAT, dateTimeZoneFormats);
        loadDateTimeEntries(configG, DATE_TIME_OFFSET_FORMAT, dateTimeOffsetFormats);
        loadDateTimeEntries(configG, DATE_TIME_FORMAT, dateTimeFormats);
        loadDateTimeEntries(configG, DATE_FORMAT, dateFormats);
    }

    /**
     * Helper function to read the date time formats from the config file, parse them, and store them in the appropriate
     * DateTimeFormatter list for use later
     *
     * @param configG the Configurator object to load entries from
     * @param entryType the label that used in the config file for the category of format. This separates out the different
     *        formats that need to be parsed differently
     * @param dateFormats the list of DateTimeFormatter objects that corresponds to the appropriate format
     */
    private static void loadDateTimeEntries(Configurator configG, String entryType, List<DateTimeFormatter> dateFormats) {
        for (final String dfentry : configG.findEntries(entryType)) {
            try {
                DateTimeFormatter initialSdf =
                        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(dfentry).toFormatter();
                if (entryType.equals(DATE_TIME_ZONE_FORMAT)) {
                    initialSdf = initialSdf.withZone(GMT);
                }
                final DateTimeFormatter sdf = initialSdf;
                dateFormats.add(sdf);
            } catch (Exception ex) {
                logger.debug("{} entry '{}' cannot be parsed", entryType, dfentry, ex);
            }
        }
        logger.debug("Loaded {} {} entries", dateTimeZoneFormats.size(), entryType);
    }

    /**
     * Parse an RFC-822 Date or one of the thousands of variants make a quick attempt to normalize the timezone information
     * and get the timestamp in GMT. Should change to pass in a default from the U124 header
     *
     * @param dateString the string date from the RFC 822 Date header
     * @param supplyDefaultOnBad when true use current date if sentDate is unparseable
     * @return the GMT time of the event or NOW if unparseable, or null if supplyDefaultOnBad is false
     */
    public static LocalDateTime parseDate(final String dateString, final boolean supplyDefaultOnBad) {

        if (dateString != null && dateString.length() > 0) {
            // Take it apart and stick it back together to
            // get rid of multiple contiguous spaces
            String instr = dateString.replaceAll("\t+", " "); // tabs
            instr = instr.replaceAll("[ ]+", " "); // multiple spaces
            instr = instr.replaceAll("=0D$", ""); // common qp'ified ending

            LocalDateTime date;
            date = tryParseWithDateTimeZoneFormats(instr);
            if (date != null)
                return date;

            date = tryParseWithDateTimeOffsetFormats(instr);
            if (date != null)
                return date;

            date = tryParseWithDateTimeFormats(instr);
            if (date != null)
                return date;

            date = tryParseWithDateFormats(instr);
            if (date != null)
                return date;

            try {
                return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(instr)).atZone(GMT).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // ignore
            }

            // If none of these methods worked, use the default if required
            if (supplyDefaultOnBad) {
                return LocalDateTime.now();
            }
        }
        return null;
    }


    /**
     * Attempt to parse the string instr with one of the ZonedDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeZoneFormats(final String instr) {
        // formats with a time zone
        for (final DateTimeFormatter dtf : dateTimeZoneFormats) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(instr, dtf);
                return ZonedDateTime.ofInstant(zdt.toInstant(), GMT).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the LocalDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeFormats(final String instr) {
        // formats with a date and time and no zone/offset
        for (final DateTimeFormatter dtf : dateTimeFormats) {
            try {
                return LocalDateTime.parse(instr, dtf);
            } catch (DateTimeParseException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the OffsetDateTime patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateTimeOffsetFormats(final String instr) {
        // formats with a time zone offset
        for (final DateTimeFormatter dtf : dateTimeOffsetFormats) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(instr, dtf);
                return OffsetDateTime.ofInstant(odt.toInstant(), GMT).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Attempt to parse the string instr with one of the LocalDate patterns
     *
     * @param instr the string to attempt to format
     * @return the LocalDateTime object if a formatter worked, or null otherwise
     */
    private static LocalDateTime tryParseWithDateFormats(final String instr) {
        // formats with a date but no time
        for (final DateTimeFormatter dtf : dateFormats) {
            try {
                LocalDate d = LocalDate.parse(instr, dtf);
                return d.atStartOfDay();
            } catch (DateTimeParseException e) {
                // ignore
            }
        }
        return null;
    }
}
