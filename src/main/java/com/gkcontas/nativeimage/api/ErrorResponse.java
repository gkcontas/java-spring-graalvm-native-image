package com.gkcontas.nativeimage.api;

import java.util.List;

public record ErrorResponse(int status, String error, List<String> details) {
}
