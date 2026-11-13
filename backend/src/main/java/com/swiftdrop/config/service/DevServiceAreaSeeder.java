package com.swiftdrop.config.service;

import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.repository.DeliveryZoneRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo service areas for the dev profile, modelled on the Service Areas design: about eight Sri Lankan
 * zones with their cities and postal codes. Runs only when {@code delivery_zones} is empty, so it
 * never touches zones someone has made; never runs in tests or production (dev profile only). Goes
 * through {@link ServiceAreaService}, so the usual rules apply.
 *
 * <p>Colombo is split by postal code (city "Colombo", 00100-00700), as in the dev tracking demo's
 * addresses; the other zones cover whole cities. Matara is created inactive to show that state.
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class DevServiceAreaSeeder implements ApplicationRunner {

    static final List<CreateServiceAreaRequest> ZONES = List.of(
            zone("Colombo 01", "Fort and the business district", true,
                    place("Colombo", "Colombo", "00100")),
            zone("Colombo 02-07", "Central Colombo", true,
                    place("Colombo", "Colombo", "00200"), place("Colombo", "Colombo", "00300"),
                    place("Colombo", "Colombo", "00400"), place("Colombo", "Colombo", "00500"),
                    place("Colombo", "Colombo", "00600"), place("Colombo", "Colombo", "00700")),
            zone("Colombo Suburbs", "Dehiwala, Mount Lavinia, Kotte and Nugegoda", true,
                    place("Colombo", "Dehiwala", null), place("Colombo", "Mount Lavinia", null),
                    place("Colombo", "Kotte", null), place("Colombo", "Nugegoda", null),
                    place("Colombo", "Maharagama", null)),
            zone("Gampaha", "Gampaha district including Negombo", true,
                    place("Gampaha", "Gampaha", null), place("Gampaha", "Negombo", null),
                    place("Gampaha", "Ja-Ela", null)),
            zone("Kandy", "Kandy and nearby towns", true,
                    place("Kandy", "Kandy", null), place("Kandy", "Peradeniya", null),
                    place("Kandy", "Katugastota", null)),
            zone("Galle", "Galle and the southern coast", true,
                    place("Galle", "Galle", null), place("Galle", "Hikkaduwa", null),
                    place("Galle", "Unawatuna", null)),
            zone("Kurunegala", "North Western Province hub", true,
                    place("Kurunegala", "Kurunegala", null)),
            zone("Matara", "Not served yet", false,
                    place("Matara", "Matara", null)));

    private final DeliveryZoneRepository zoneRepository;
    private final ServiceAreaService serviceAreaService;

    @Override
    public void run(ApplicationArguments args) {
        if (zoneRepository.count() > 0) {
            return;
        }
        ZONES.forEach(serviceAreaService::create);
        log.info("Dev profile: added {} demo service areas", ZONES.size());
    }

    private static CreateServiceAreaRequest zone(String name, String description, boolean active,
                                                 CoverageAreaRequest... places) {
        return new CreateServiceAreaRequest(name, description, active, List.of(places));
    }

    private static CoverageAreaRequest place(String district, String city, String postalCode) {
        return new CoverageAreaRequest(district, city, postalCode);
    }
}
