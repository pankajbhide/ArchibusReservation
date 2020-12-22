package com.archibus.app.reservation.ics.domain;

/**
 * Helper object to contain the message reference details.
 * <p>
 *
 * @author PROCOS
 * @since 23.2
 *
 */
public final class MessageReferenceModel {

    /**
     * The Activity Id property.
     */
    private final String activity;

    /**
     * The Referenced By property.
     */
    private final String referenced;

    /**
     * The locale property.
     */
    private final String locale;

    /**
     * Default constructor specifying the message reference details.
     *
     * @param activityId the Activity Id
     * @param referencedBy the Referenced By
     * @param localeName the locale
     */
    public MessageReferenceModel(final String activityId,
            final String referencedBy, final String localeName) {
        this.activity = activityId;
        this.referenced = referencedBy;
        this.locale = localeName;
    }

    /**
     * Getter for the activity property.
     *
     * @return the activity property.
     */
    public String getActivityId() {
        return this.activity;
    }

    /**
     * Getter for the referenced property.
     *
     * @return the referenced property.
     */
    public String getReferencedBy() {
        return this.referenced;
    }

    /**
     * Getter for the locale property.
     *
     * @return the locale property.
     */
    public String getLocale() {
        return this.locale;
    }
}
