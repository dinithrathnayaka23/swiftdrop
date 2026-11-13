package com.swiftdrop.config.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.config.dto.request.CoverageAreaRequest;
import com.swiftdrop.config.dto.request.CreateServiceAreaRequest;
import com.swiftdrop.config.dto.response.CoverageAreaResponse;
import com.swiftdrop.config.dto.response.ServiceAreaDetailResponse;
import com.swiftdrop.config.service.ServiceAreaService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminServiceAreaControllerTest {

    private static final UUID ZONE_ID = UUID.randomUUID();

    @Mock private ServiceAreaService service;
    @InjectMocks private AdminServiceAreaController controller;

    private static ServiceAreaDetailResponse detail() {
        return new ServiceAreaDetailResponse(ZONE_ID, "Kandy", null, true, List.of(), List.of(), null, 0,
                Instant.now(), List.of());
    }

    @Test
    void onlyTheDispatcherAdminMayUseIt() {
        assertThat(AdminServiceAreaController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void listsWithTheFiltersAndRefusesPagesThatDoNotExist() {
        when(service.list("kan", true, 1, 50)).thenReturn(Page.empty());
        assertThat(controller.list("kan", true, 1, 50).getContent()).isEmpty();

        assertThatThrownBy(() -> controller.list(null, null, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createAnswers201WithTheLocation() {
        CreateServiceAreaRequest request = new CreateServiceAreaRequest("Kandy", null, null, null);
        when(service.create(request)).thenReturn(detail());

        var response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/admin/service-areas/" + ZONE_ID);
    }

    @Test
    void coverageIsAddedWith201AndRemovedWith204() {
        CoverageAreaRequest place = new CoverageAreaRequest("Kandy", "Peradeniya", null);
        UUID coverageId = UUID.randomUUID();
        when(service.addCoverage(ZONE_ID, place))
                .thenReturn(new CoverageAreaResponse(coverageId, "Kandy", "Peradeniya", null, true));

        assertThat(controller.addCoverage(ZONE_ID, place).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.removeCoverage(ZONE_ID, coverageId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).removeCoverage(ZONE_ID, coverageId);
    }

    @Test
    void activateAndDeactivateGoToTheService() {
        when(service.activate(ZONE_ID)).thenReturn(detail());
        when(service.deactivate(ZONE_ID)).thenReturn(detail());

        controller.activate(ZONE_ID);
        controller.deactivate(ZONE_ID);

        verify(service).activate(ZONE_ID);
        verify(service).deactivate(ZONE_ID);
    }

    @Test
    void badPagesNeverReachTheService() {
        assertThatThrownBy(() -> controller.list(null, null, 0, 1000)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(service);
    }
}
