package com.example.authservice.mapper;

import com.example.authservice.dto.CustomerSummaryResponse;
import com.example.authservice.dto.UserProfileResponse;
import com.example.authservice.dto.UserResponse;
import com.example.authservice.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    UserProfileResponse toProfileResponse(User user);

    CustomerSummaryResponse toCustomerSummary(User user);
}
