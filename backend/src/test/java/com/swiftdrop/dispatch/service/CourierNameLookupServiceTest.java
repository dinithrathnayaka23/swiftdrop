package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.dispatch.repository.OpenAssignmentCourier;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourierNameLookupServiceTest {

    private static final UUID RAMESH = UUID.randomUUID();
    private static final UUID NIMAL = UUID.randomUUID();

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private UserApi userApi;
    @InjectMocks private CourierNameLookupService service;

    private static UserSummary account(UUID id, String name) {
        return new UserSummary(id, name, "courier@example.com", "+94771234567", Role.COURIER, AccountStatus.ACTIVE,
                true, Instant.now());
    }

    @Test
    void namesTheCourierOfEachAssignedParcelReadingEachCourierOnce() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID waiting = UUID.randomUUID();
        when(assignmentRepository.findOpenCouriersByParcelIds(Set.of(first, second, third, waiting))).thenReturn(List.of(
                new OpenAssignmentCourier(first, RAMESH),
                new OpenAssignmentCourier(second, RAMESH),
                new OpenAssignmentCourier(third, NIMAL)));
        when(userApi.findUser(RAMESH)).thenReturn(Optional.of(account(RAMESH, "Ramesh Perera")));
        when(userApi.findUser(NIMAL)).thenReturn(Optional.of(account(NIMAL, "Nimal Silva")));

        Map<UUID, String> names = service.currentCourierNames(List.of(first, second, third, waiting));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(
                first, "Ramesh Perera", second, "Ramesh Perera", third, "Nimal Silva"));
        assertThat(names).doesNotContainKey(waiting); // no open assignment
        verify(assignmentRepository, times(1)).findOpenCouriersByParcelIds(Set.of(first, second, third, waiting));
        verify(userApi, times(1)).findUser(RAMESH);
        verify(userApi, times(1)).findUser(NIMAL);
    }

    @Test
    void skipsACourierWhoseAccountCannotBeRead() {
        UUID parcel = UUID.randomUUID();
        when(assignmentRepository.findOpenCouriersByParcelIds(Set.of(parcel)))
                .thenReturn(List.of(new OpenAssignmentCourier(parcel, RAMESH)));
        when(userApi.findUser(RAMESH)).thenReturn(Optional.empty());

        assertThat(service.currentCourierNames(List.of(parcel))).isEmpty();
    }

    @Test
    void noParcelsNeedNoQueries() {
        assertThat(service.currentCourierNames(List.of())).isEmpty();
        assertThat(service.currentCourierNames(null)).isEmpty();
        verifyNoInteractions(assignmentRepository, userApi);
    }
}
