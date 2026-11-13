package com.swiftdrop.config.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.dto.response.ServiceAreaResponse;
import com.swiftdrop.config.model.DeliveryZone;
import com.swiftdrop.config.model.ZoneCoverageArea;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceAreaMapperTest {

    private static final DeliveryZone ZONE = new DeliveryZone();

    static {
        ZONE.setId(UUID.randomUUID());
        ZONE.setZoneName("Colombo 02-07");
    }

    private static ZoneCoverageArea area(String district, String city, String postalCode, boolean active) {
        ZoneCoverageArea area = new ZoneCoverageArea();
        area.setId(UUID.randomUUID());
        area.setZone(ZONE);
        area.setDistrict(district);
        area.setCity(city);
        area.setPostalCode(postalCode);
        area.setActive(active);
        return area;
    }

    @Test
    void summarisesOnlyActiveRowsWithDistinctSortedPlaces() {
        ServiceAreaResponse row = ServiceAreaMapper.toRow(ZONE, List.of(
                area("Colombo", "Wellawatte", "00600", true),
                area("Colombo", "Kollupitiya", "00300", true),
                area("colombo", "kollupitiya", "00301", true),
                area("Gampaha", "Negombo", "11500", false)));

        assertThat(row.cities()).containsExactly("Kollupitiya", "Wellawatte");
        assertThat(row.districts()).containsExactly("Colombo");
        assertThat(row.postalCodeRange()).isEqualTo("00300-00600");
        assertThat(row.coverageCount()).isEqualTo(3);
    }

    @Test
    void postalCodeRangeIsTheOneCodeOrNothing() {
        assertThat(ServiceAreaMapper.postalCodeRange(List.of(area("Kandy", "Kandy", "20000", true))))
                .isEqualTo("20000");
        assertThat(ServiceAreaMapper.postalCodeRange(List.of(area("Kandy", "Kandy", null, true)))).isNull();
        assertThat(ServiceAreaMapper.postalCodeRange(List.of())).isNull();
    }

    @Test
    void detailListsEveryRowByPlaceWithCityWideRowsFirst() {
        ServiceAreaDetailResponse detail = ServiceAreaMapper.toDetail(ZONE, List.of(
                area("Colombo", "Kollupitiya", "00300", true),
                area("Colombo", "Kollupitiya", null, false),
                area("Colombo", "Bambalapitiya", "00400", true)));

        assertThat(detail.coverageAreas()).extracting(CoverageAreaResponse::city, CoverageAreaResponse::postalCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Bambalapitiya", "00400"),
                        org.assertj.core.groups.Tuple.tuple("Kollupitiya", null),
                        org.assertj.core.groups.Tuple.tuple("Kollupitiya", "00300"));
        assertThat(detail.coverageCount()).isEqualTo(2);
    }
}
