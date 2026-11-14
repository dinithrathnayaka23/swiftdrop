package com.swiftdrop.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.usermanagement.dto.response.UserStatsResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserStatsServiceTest {

    @Mock private UserApi userApi;
    @InjectMocks private UserStatsService userStatsService;

    /** A page whose total is {@code total}, as UserApi returns for a size-1 request. */
    private static Page<UserSummary> pageWithTotal(long total) {
        return new PageImpl<>(List.of(), PageRequest.of(0, 1), total);
    }

    private void given(UserSearch search, long total) {
        when(userApi.listUsers(eq(search), any())).thenReturn(pageWithTotal(total));
    }

    @Test
    void countsUsersInTotalPerRoleAndPerStatus() {
        when(userApi.listUsers(any(), any())).thenReturn(pageWithTotal(0));
        given(UserSearch.all(), 12);
        given(new UserSearch(null, Role.CUSTOMER, null), 7);
        given(new UserSearch(null, Role.MERCHANT, null), 2);
        given(new UserSearch(null, Role.COURIER, null), 2);
        given(new UserSearch(null, Role.DISPATCHER_ADMIN, null), 1);
        given(new UserSearch(null, null, AccountStatus.ACTIVE), 10);
        given(new UserSearch(null, null, AccountStatus.PENDING_APPROVAL), 1);
        given(new UserSearch(null, null, AccountStatus.DEACTIVATED), 1);

        UserStatsResponse stats = userStatsService.stats();

        assertThat(stats.total()).isEqualTo(12);
        assertThat(stats.byRole()).containsEntry(Role.CUSTOMER, 7L).containsEntry(Role.MERCHANT, 2L)
                .containsEntry(Role.COURIER, 2L).containsEntry(Role.DISPATCHER_ADMIN, 1L);
        assertThat(stats.byStatus()).containsEntry(AccountStatus.ACTIVE, 10L)
                .containsEntry(AccountStatus.PENDING_APPROVAL, 1L)
                .containsEntry(AccountStatus.DEACTIVATED, 1L);
    }

    @Test
    void everyRoleAndStatusIsListedEvenWhenZero() {
        when(userApi.listUsers(any(), any())).thenReturn(pageWithTotal(0));

        UserStatsResponse stats = userStatsService.stats();

        assertThat(stats.total()).isZero();
        assertThat(stats.byRole()).containsOnlyKeys(Role.values()).allSatisfy((role, count) -> assertThat(count).isZero());
        assertThat(stats.byStatus()).containsOnlyKeys(AccountStatus.values())
                .allSatisfy((status, count) -> assertThat(count).isZero());
    }

    @Test
    void onlyAskForOneRowPerCount() {
        when(userApi.listUsers(any(), eq(PageRequest.of(0, 1)))).thenReturn(pageWithTotal(3));

        assertThat(userStatsService.stats().total()).isEqualTo(3);
    }
}
