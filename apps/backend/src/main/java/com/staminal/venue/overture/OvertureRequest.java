package com.staminal.venue.overture;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OvertureRequest {
    private OvertureRequest() { }
    public record Preview(@NotEmpty @Size(max = 20) List<@NotBlank String> ids) { }
    public record Import(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String catalogVersion,
            @NotEmpty @Size(max = 20) List<@NotBlank String> ids) { }
}
