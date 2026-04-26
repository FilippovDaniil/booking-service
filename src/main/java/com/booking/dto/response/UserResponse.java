package com.booking.dto.response;

import com.booking.entity.User;
import com.booking.entity.enums.Role;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setRole(user.getRole());
        r.setEnabled(user.isEnabled());
        r.setCreatedAt(user.getCreatedAt());
        return r;
    }
}
