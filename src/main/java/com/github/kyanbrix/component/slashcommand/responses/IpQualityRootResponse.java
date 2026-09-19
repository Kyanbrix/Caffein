package com.github.kyanbrix.api;

import java.util.ArrayList;
import java.util.Date;

public class IpQualityRoot {

    public static class DomainAge {
        public String human;
        public int timestamp;
        public Date iso;
    }


    public String message;
    public boolean success;
    public boolean unsafe;
    public String domain;
    public String root_domain;
    public String ip_address;
    public String country_code;
    public String language_code;
    public String server;
    public String content_type;
    public int status_code;
    public int page_size;
    public int domain_rank;
    public boolean dns_valid;
    public boolean parking;
    public boolean spamming;
    public boolean malware;
    public boolean phishing;
    public boolean suspicious;
    public String domain_trust;
    public boolean short_link_redirect;
    public boolean hosted_content;
    public String page_title;
    public boolean risky_tld;
    public boolean spf_record;
    public boolean dmarc_record;
    public ArrayList<String> technologies;
    public ArrayList<String> a_records;
    public ArrayList<String> mx_records;
    public ArrayList<String> ns_records;
    public boolean adult;
    public int risk_score;
    public DomainAge domain_age;
    public String category;
    public boolean redirected;
    public String scanned_url;
    public String final_url;
    public String request_id;
}
