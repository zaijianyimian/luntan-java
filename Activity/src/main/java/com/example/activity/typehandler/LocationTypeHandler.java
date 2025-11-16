package com.example.activity.typehandler;

import com.example.activity.pointhandler.GeoPoint;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LocationTypeHandler extends BaseTypeHandler<GeoPoint> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, GeoPoint parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setObject(i, null);
            return;
        }
        String wkt = "POINT(" + parameter.getLongitude() + " " + parameter.getLatitude() + ")";
        ps.setString(i, wkt);
    }

    @Override
    public GeoPoint getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parsePoint(rs.getString(columnName));
    }

    @Override
    public GeoPoint getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parsePoint(rs.getString(columnIndex));
    }

    @Override
    public GeoPoint getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parsePoint(cs.getString(columnIndex));
    }

    private GeoPoint parsePoint(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        try {
            if (t.startsWith("POINT")) {
                int l = t.indexOf('(');
                int r = t.indexOf(')');
                if (l >= 0 && r > l) {
                    String inner = t.substring(l + 1, r).trim();
                    String[] parts = inner.split("\\s+");
                    if (parts.length >= 2) {
                        double lon = Double.parseDouble(parts[0]);
                        double lat = Double.parseDouble(parts[1]);
                        return new GeoPoint(lon, lat);
                    }
                }
            } else if (t.contains(",")) {
                String[] parts = t.split(",");
                double lon = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                return new GeoPoint(lon, lat);
            } else if (t.contains(" ")) {
                String[] parts = t.split("\\s+");
                double lon = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                return new GeoPoint(lon, lat);
            }
        } catch (Exception ignore) {}
        return null;
    }
}
