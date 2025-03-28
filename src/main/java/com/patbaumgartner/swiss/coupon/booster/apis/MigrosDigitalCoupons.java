package com.patbaumgartner.swiss.coupon.booster.apis;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class MigrosDigitalCoupons {

    public List<Available> available = new ArrayList<>();
    public List<Activated> activated = new ArrayList<>();
    public List<Preview> preview = new ArrayList<>();
    public List<Redeemed> redeemed = new ArrayList<>();
    public List<Partner> partner = new ArrayList<>();

    @Data
    static class Activated {
        public String id;
        public int quantity;
        public String status;
        public boolean preview;
        public Object redeemed;
        public String gtin;
        public String language;
        public String typeId;
        public String variant;
        public Object promocode;
        public String name;
        public String subtitle;
        public String discountType;
        public String discountAmount;
        public String discountAmountValue;
        public String minimumPurchase;
        public double minimumPurchaseValue;
        public String validFrom;
        public String validTo;
        public boolean digital;
        public boolean stationaryRedeemable;
        public boolean personal;
        public boolean onlineRedeemable;
        public String customerGroup;
        public Signet signet;
        public SignetInactive signetInactive;
        public Object signetBonuscouponUrl;
        public String imageUrl;
        public String imageUrlInactive;
        public ArrayList<String> previews;
        public Links links;
        public String promotionNumber;
        public String redeemableAt;
        public String disclaimer;
        public DistributionChannels distributionChannels;
        public Object redeemableArea;
        public ArrayList<String> regions;
        public Object campaignColors;
        public Object campaignId;
        public int matchingProducts;
        public boolean wholeAssortment;
        public Object finePrint;
    }

    @Data
    static class Available {
        public String id;
        public int quantity;
        public String status;
        public boolean preview;
        public Object redeemed;
        public String gtin;
        public String language;
        public String typeId;
        public String variant;
        public Object promocode;
        public String name;
        public String subtitle;
        public String discountType;
        public String discountAmount;
        public String discountAmountValue;
        public String minimumPurchase;
        public Object minimumPurchaseValue;
        public String validFrom;
        public String validTo;
        public boolean digital;
        public boolean stationaryRedeemable;
        public boolean personal;
        public boolean onlineRedeemable;
        public String customerGroup;
        public Signet signet;
        public SignetInactive signetInactive;
        public Object signetBonuscouponUrl;
        public String imageUrl;
        public String imageUrlInactive;
        public ArrayList<String> previews;
        public Links links;
        public String promotionNumber;
        public String redeemableAt;
        public String disclaimer;
        public DistributionChannels distributionChannels;
        public Object redeemableArea;
        public ArrayList<String> regions;
        public Object campaignColors;
        public Object campaignId;
        public int matchingProducts;
        public boolean wholeAssortment;
        public Object finePrint;
    }

    @Data
    static class CampaignColors {
        public String dark;
        public String light;
    }

    @Data
    static class Channel {
        public String id;
        public String imageUrl;
        public String imageUrlInactive;
        public String name;
        public String filterId;
        public String filter;
    }

    @Data
    static class DistributionChannels {
        public String text;
        public ArrayList<Channel> channels;
    }

    @Data
    static class Links {
        public Object onlineshop;
        public Object note;
    }

    @Data
    static class Partner {
        public String id;
        public Object quantity;
        public Object status;
        public boolean preview;
        public Object redeemed;
        public String gtin;
        public String language;
        public String typeId;
        public String variant;
        public String promocode;
        public String name;
        public String subtitle;
        public String discountType;
        public String discountAmount;
        public String discountAmountValue;
        public String minimumPurchase;
        public Object minimumPurchaseValue;
        public String validFrom;
        public String validTo;
        public boolean digital;
        public boolean stationaryRedeemable;
        public boolean personal;
        public boolean onlineRedeemable;
        public String customerGroup;
        public Signet signet;
        public SignetInactive signetInactive;
        public Object signetBonuscouponUrl;
        public String imageUrl;
        public String imageUrlInactive;
        public ArrayList<String> previews;
        public Links links;
        public String promotionNumber;
        public String redeemableAt;
        public String disclaimer;
        public DistributionChannels distributionChannels;
        public Object redeemableArea;
        public ArrayList<String> regions;
        public Object campaignColors;
        public Object campaignId;
        public Object matchingProducts;
        public boolean wholeAssortment;
        public String finePrint;
    }

    @Data
    static class Preview {
        public String id;
        public int quantity;
        public String status;
        public boolean preview;
        public Object redeemed;
        public String gtin;
        public String language;
        public String typeId;
        public String variant;
        public Object promocode;
        public String name;
        public String subtitle;
        public String discountType;
        public String discountAmount;
        public String discountAmountValue;
        public String minimumPurchase;
        public Object minimumPurchaseValue;
        public String validFrom;
        public String validTo;
        public boolean digital;
        public boolean stationaryRedeemable;
        public boolean personal;
        public boolean onlineRedeemable;
        public String customerGroup;
        public Signet signet;
        public SignetInactive signetInactive;
        public Object signetBonuscouponUrl;
        public String imageUrl;
        public String imageUrlInactive;
        public ArrayList<String> previews;
        public Links links;
        public String promotionNumber;
        public String redeemableAt;
        public String disclaimer;
        public DistributionChannels distributionChannels;
        public Object redeemableArea;
        public ArrayList<String> regions;
        public CampaignColors campaignColors;
        public Object campaignId;
        public int matchingProducts;
        public boolean wholeAssortment;
        public Object finePrint;
    }

    @Data
    static class Redeemed {
        public String id;
        public int quantity;
        public String status;
        public boolean preview;
        public Date redeemed;
        public String gtin;
        public String language;
        public String typeId;
        public String variant;
        public Object promocode;
        public String name;
        public String subtitle;
        public String discountType;
        public String discountAmount;
        public String discountAmountValue;
        public String minimumPurchase;
        public Object minimumPurchaseValue;
        public Object validFrom;
        public String validTo;
        public boolean digital;
        public boolean stationaryRedeemable;
        public boolean personal;
        public boolean onlineRedeemable;
        public String customerGroup;
        public Signet signet;
        public SignetInactive signetInactive;
        public String signetBonuscouponUrl;
        public String imageUrl;
        public String imageUrlInactive;
        public ArrayList<String> previews;
        public Links links;
        public String promotionNumber;
        public String redeemableAt;
        public String disclaimer;
        public DistributionChannels distributionChannels;
        public Object redeemableArea;
        public ArrayList<String> regions;
        public Object campaignColors;
        public Object campaignId;
        public Object matchingProducts;
        public boolean wholeAssortment;
        public Object finePrint;
    }

    @Data
    static class Signet {
        public String imageUrl;
        public String hexColor;
        public Object logo;
    }

    @Data
    static class SignetInactive {
        public String imageUrl;
        public String hexColor;
        public Object logo;
    }
}