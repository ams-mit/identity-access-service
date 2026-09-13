package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.RoleResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.Role;
import lk.ac.kelaniya.ams.identity_access_service.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    @DisplayName("getAllRoles returns all roles mapped to RoleResponse and sorted by name")
    void testGetAllRoles_returnsSortedRoles() {
        Role role1 = Role.builder().id(UUID.randomUUID()).name("TENANT_RESIDENT").description("Tenant or Resident").build();
        Role role2 = Role.builder().id(UUID.randomUUID()).name("SYSTEM_ADMINISTRATOR").description("Full administrative access").build();
        Role role3 = Role.builder().id(UUID.randomUUID()).name("APARTMENT_MANAGER").description("Apartment Manager").build();

        given(roleRepository.findAll()).willReturn(List.of(role1, role2, role3));

        List<RoleResponse> result = roleService.getAllRoles();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("APARTMENT_MANAGER");
        assertThat(result.get(1).getName()).isEqualTo("SYSTEM_ADMINISTRATOR");
        assertThat(result.get(2).getName()).isEqualTo("TENANT_RESIDENT");
        assertThat(result.get(0).getId()).isEqualTo(role3.getId());
        assertThat(result.get(0).getDescription()).isEqualTo("Apartment Manager");
    }

    @Test
    @DisplayName("getAllRoles returns empty list when no roles in repository")
    void testGetAllRoles_emptyRepository_returnsEmptyList() {
        given(roleRepository.findAll()).willReturn(List.of());

        List<RoleResponse> result = roleService.getAllRoles();

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
