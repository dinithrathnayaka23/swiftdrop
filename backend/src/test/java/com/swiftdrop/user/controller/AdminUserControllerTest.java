package com.swiftdrop.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.request.ChangeAccountStatusRequest;
import com.swiftdrop.user.dto.request.ChangeRoleRequest;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private AdminUserController controller;

    @Test
    void onlyTheAdminMayUseIt() {
        assertThat(AdminUserController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void listsWithTheFiltersAndPageAsked() {
        UserSummary user = Mockito.mock(UserSummary.class);
        when(userService.listUsers(any(), any())).thenReturn(new PageImpl<>(List.of(user)));

        var page = controller.list("nimal", Role.COURIER, AccountStatus.ACTIVE, 1, 50);

        verify(userService).listUsers(new UserSearch("nimal", Role.COURIER, AccountStatus.ACTIVE), PageRequest.of(1, 50));
        assertThat(page.getContent()).containsExactly(user);
    }

    @Test
    void refusesPagesThatDoNotExist() {
        assertThatThrownBy(() -> controller.list(null, null, null, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, null, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(null, null, null, 0, 101)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(userService);
    }

    @Test
    void changesTheRoleAndStatusThroughTheAdminRules() {
        UUID userId = UUID.randomUUID();

        controller.changeRole(userId, new ChangeRoleRequest(Role.COURIER));
        controller.changeStatus(userId, new ChangeAccountStatusRequest(AccountStatus.SUSPENDED));

        verify(userService).changeRole(userId, Role.COURIER);
        verify(userService).changeAccountStatusAsAdmin(userId, AccountStatus.SUSPENDED);
    }
}
