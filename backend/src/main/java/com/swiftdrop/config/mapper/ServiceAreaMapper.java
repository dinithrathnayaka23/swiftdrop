package com.swiftdrop.config.mapper;

import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.dto.response.ServiceAreaResponse;
import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

public final class ServiceAreaMapper {

    /** District, city, then postal code (city-wide rows first), ignoring case. */
    static final Comparator<ZoneCoverageArea> PLACE_ORDER = Comparator
            .comparing(ZoneCoverageArea::getDistrict, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ZoneCoverageArea::getCity, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ZoneCoverageArea::getPostalCode, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));

    private ServiceAreaMapper() {
    }

    /** A list row; {@code coverage} is all of the zone's rows (only active ones are summarised). */
    public static ServiceAreaResponse toRow(DeliveryZone zone, List<ZoneCoverageArea> coverage) {
        List<ZoneCoverageArea> active = coverage.stream().filter(ZoneCoverageArea::isActive).toList();
        return new ServiceAreaResponse(zone.getId(), zone.getZoneName(), zone.getDescription(), zone.isActive(),
                distinct(active, ZoneCoverageArea::getCity), distinct(active, ZoneCoverageArea::getDistrict),
                postalCodeRange(active), active.size(), zone.getCreatedAt());
    }

    /** One zone with every coverage row. */
    public static ServiceAreaDetailResponse toDetail(DeliveryZone zone, List<ZoneCoverageArea> coverage) {
        ServiceAreaResponse row = toRow(zone, coverage);
        return new ServiceAreaDetailResponse(row.id(), row.name(), row.description(), row.active(), row.cities(),
                row.districts(), row.postalCodeRange(), row.coverageCount(), row.createdAt(),
                coverage.stream().sorted(PLACE_ORDER).map(ServiceAreaMapper::toResponse).toList());
    }

    public static CoverageAreaResponse toResponse(ZoneCoverageArea area) {
        return new CoverageAreaResponse(area.getId(), area.getDistrict(), area.getCity(), area.getPostalCode(),
                area.isActive());
    }

    /** Distinct ignoring case (the first spelling wins), sorted ignoring case. */
    private static List<String> distinct(List<ZoneCoverageArea> rows, Function<ZoneCoverageArea, String> field) {
        TreeSet<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        rows.stream().map(field).forEach(values::add);
        return List.copyOf(values);
    }

    /** "lowest-highest" (string order; Sri Lankan postal codes are 5 digits), the one code, or {@code null}. */
    static String postalCodeRange(List<ZoneCoverageArea> activeRows) {
        TreeSet<String> codes = new TreeSet<>();
        activeRows.stream().map(ZoneCoverageArea::getPostalCode).filter(code -> code != null).forEach(codes::add);
        if (codes.isEmpty()) {
            return null;
        }
        return codes.size() == 1 ? codes.first() : codes.first() + "-" + codes.last();
    }
}
