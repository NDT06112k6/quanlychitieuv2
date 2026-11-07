package com.example.expensetracker.user;

import com.example.expensetracker.auth.JwtService;
import com.example.expensetracker.common.web.ApiRoutes;
import com.example.expensetracker.support.ControllerTestSupport;
import com.example.expensetracker.support.DomainFixtures;
import com.example.expensetracker.support.RestControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.example.expensetracker.support.DomainFixtures.USER_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RestControllerTest(UserController.class)
class UserControllerTests extends ControllerTestSupport {

    @MockitoBean
    private UserService userService;

    @Autowired
    UserControllerTests(org.springframework.test.web.servlet.MockMvc mockMvc, JwtService jwtService) {
        super(mockMvc, jwtService);
    }

    @Test
    void getsTheTokenOwnersProfileDespiteForgedQueryParameter() throws Exception {
        when(userService.getProfile(USER_ID)).thenReturn(UserResponse.from(DomainFixtures.user()));

        mockMvc.perform(get(ApiRoutes.USER_ME).param("userId", "999")
                        .header(HttpHeaders.AUTHORIZATION, authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).getProfile(USER_ID);
    }

    @Test
    void updatesOnlyTheTokenOwnersDisplayName() throws Exception {
        UserProfileRequest request = new UserProfileRequest("Changed");
        when(userService.updateProfile(USER_ID, request)).thenReturn(UserResponse.from(DomainFixtures.user()));

        mockMvc.perform(put(ApiRoutes.USER_ME).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Changed\",\"userId\":999}"))
                .andExpect(status().isOk());

        verify(userService).updateProfile(USER_ID, request);
    }

    @Test
    void changesPasswordForTheTokenOwner() throws Exception {
        String body = """
                {"currentPassword":"old-password","newPassword":"new-password"}
                """;

        mockMvc.perform(put(ApiRoutes.USER_PASSWORD).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(userService).changePassword(USER_ID, new PasswordChangeRequest("old-password", "new-password"));
    }

    @Test
    void rejectsInvalidProfileAndPasswordBodies() throws Exception {
        mockMvc.perform(put(ApiRoutes.USER_ME).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(ApiRoutes.USER_PASSWORD).header(HttpHeaders.AUTHORIZATION, authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get(ApiRoutes.USER_ME)).andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }
}
