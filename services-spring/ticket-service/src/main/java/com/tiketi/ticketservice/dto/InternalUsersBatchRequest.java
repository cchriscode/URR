package com.tiketi.ticketservice.dto;

import java.util.List;

public record InternalUsersBatchRequest(List<String> userIds) {
}
