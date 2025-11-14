package com.example.activity.config;

import com.example.activity.pointhandler.GeoPoint;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.IOException;

public class PointDeserializer extends JsonDeserializer<GeoPoint> {
    @Override
    public GeoPoint deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var token = p.currentToken();

        if (token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
            String pointStr = p.getValueAsString();
            if (pointStr == null || pointStr.trim().isEmpty()) {
                return null;
            }
            String regex = "POINT\\(([^\\s]+)\\s+([^\\s]+)\\)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(pointStr);
            if (matcher.find()) {
                double lon = Double.parseDouble(matcher.group(1));
                double lat = Double.parseDouble(matcher.group(2));
                return new GeoPoint(lon, lat);
            }
            return null;
        }

        if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
            com.fasterxml.jackson.databind.JsonNode node = p.getCodec().readTree(p);

            Double lon = null, lat = null;
            if (node.has("longitude")) lon = node.get("longitude").asDouble();
            if (node.has("latitude"))  lat = node.get("latitude").asDouble();

            if (lon == null && node.has("lon")) lon = node.get("lon").asDouble();
            if (lat == null && node.has("lat")) lat = node.get("lat").asDouble();

            if (lon == null && node.has("lng")) lon = node.get("lng").asDouble();

            if (lon == null && node.has("x")) lon = node.get("x").asDouble();
            if (lat == null && node.has("y")) lat = node.get("y").asDouble();

            if (lon != null && lat != null) {
                return new GeoPoint(lon, lat);
            }
            return null;
        }

        if (token == com.fasterxml.jackson.core.JsonToken.VALUE_NULL) {
            return null;
        }

        String raw = p.getValueAsString();
        if (raw != null && raw.startsWith("POINT(")) {
            String regex = "POINT\\(([^\\s]+)\\s+([^\\s]+)\\)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(raw);
            if (matcher.find()) {
                double lon = Double.parseDouble(matcher.group(1));
                double lat = Double.parseDouble(matcher.group(2));
                return new GeoPoint(lon, lat);
            }
        }
        return null;
    }
}

