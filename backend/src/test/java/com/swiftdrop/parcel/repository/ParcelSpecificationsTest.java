package com.swiftdrop.parcel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParcelSpecificationsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "  Kandy |%kandy%",
            "100%|%100\\%%",
            "a_b|%a\\_b%",
            "back\\slash|%back\\\\slash%"})
    void searchTermsAreLowerCasedAndWildcardsAreTakenLiterally(String term, String pattern) {
        assertThat(ParcelSpecifications.likePattern(term)).isEqualTo(pattern);
    }
}
