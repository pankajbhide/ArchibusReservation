package com.archibus.app.reservation.domain;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Domain class for Arrangement Types.
 * 
 * Maps to rm_arrange_type table.
 * 
 * @author Bart Vanderschoot
 * 
 */
@XmlRootElement(name = "ArrangeType")
public class ArrangeType {

    /** name of arrange type. */
    private String arrangeName;

    /** id. */
    private String arrangeTypeId;

    /** The vendor. */
    private String vendor;

    /** The trade. */
    private String trade;

    /**
     * Default constructor.
     */
    public ArrangeType() {
        super();
    }

    /**
     * Constructor using parameters.
     * 
     * @param arrangeTypeId the arrange type id
     * @param arrangeName the arrange name
     */
    public ArrangeType(final String arrangeTypeId, final String arrangeName) {
        super();
        this.arrangeTypeId = arrangeTypeId;
        this.arrangeName = arrangeName;
    }

    /**
     * Gets the arrange name.
     * 
     * @return the arrange name
     */
    public final String getArrangeName() {
        return arrangeName;
    }

    /**
     * Gets the arrange type id.
     * 
     * @return the arrange type id
     */
    public final String getArrangeTypeId() {
        return arrangeTypeId;
    }

    /**
     * Sets the arrange name.
     * 
     * @param arrangeName the new arrange name
     */
    public final void setArrangeName(final String arrangeName) {
        this.arrangeName = arrangeName;
    }

    /**
     * Sets the arrange type id.
     * 
     * @param arrangeTypeId the new arrange type id
     */
    public final void setArrangeTypeId(final String arrangeTypeId) {
        this.arrangeTypeId = arrangeTypeId;
    }


    /**
     * Gets the vendor.
     *
     * @return the vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * Sets the vendor.
     *
     * @param vendor the new vendor
     */
    public void setVendor(final String vendor) {
        this.vendor = vendor;
    }

    /**
     * Gets the trade.
     *
     * @return the trade
     */
    public String getTrade() {
        return trade;
    }

    /**
     * Sets the trade.
     *
     * @param trade the new trade
     */
    public void setTrade(final String trade) {
        this.trade = trade;
    }


}
