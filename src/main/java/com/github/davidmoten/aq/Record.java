package com.github.davidmoten.aq;

import java.util.Date;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.davidmoten.guavamini.Preconditions;

public class Record {
   
    @JsonProperty("datetime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "ADST")
    public Date time;
    
    @JsonProperty("aqi_pm2_5")
    private Double value;

    @JsonProperty("name")
    private String name;
    
    Record() {
        
    }
    
    Record(String name, Date time, Optional<Double> value) {
        Preconditions.checkNotNull(value);
        this.name= name;
        this.time = time;
        this.value = value.orElse(null);
    }
    
    public Optional<Double> value() {
        return Optional.ofNullable(value);
    }
    
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "Record [name=" + name + ", time=" + time + ", value=" + value + "]";
    }
    
}
