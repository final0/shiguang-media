package org.example.springaidemo.growth;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 家庭和上传人来自登录会话；角色只允许在管理员专用接口修改。 */
public final class GrowthDtos {
    private GrowthDtos() { }
    public record Login(@NotBlank @Size(max = 64) String phone, @NotBlank @Size(max = 72) String password) { }
    public record Register(@NotBlank @Pattern(regexp = "1[0-9]{10}") String phone,
                           @NotBlank @Size(min = 8, max = 60) String password,
                           @NotBlank @Size(max = 40) String nickname,
                           @Size(max = 60) String familyName, @Size(max = 64) String inviteCode,
                           @NotBlank @Size(max=100) String email, @NotBlank @Pattern(regexp="[0-9]{6}") String emailCode) { }
    public record Child(@NotBlank @Size(max = 40) String name, @NotNull @PastOrPresent LocalDate birthday,
                        @Pattern(regexp = "girl|boy|secret") String gender) { }
    public record Entry(@Positive long childId, @NotBlank @Size(max = 80) String title,
                        @NotNull @Size(max = 4000) String content, @NotNull @PastOrPresent LocalDate recordTime,
                        @NotBlank @Pattern(regexp = "diary|milestone") String recordType,
                        @Size(max = 60) String milestone, @NotNull @Size(max = 6) List<@NotBlank @Size(max = 32) String> tags) { }
    public record Measurement(@Positive long childId, @NotNull @PastOrPresent LocalDate measureTime,
                              @NotNull @DecimalMin("20") @DecimalMax("250") BigDecimal height,
                              @NotNull @DecimalMin("0.5") @DecimalMax("200") BigDecimal weight,
                              @DecimalMin("10") @DecimalMax("100") BigDecimal head, @Size(max = 255) String remark) { }
    public record Vaccine(@Positive long childId, @NotBlank @Size(max = 64) String vaccineName,
                          @Min(1) @Max(20) int doseNo, @NotNull @PastOrPresent LocalDate vaccineDate,
                          @Size(max = 128) String hospital, @Size(max = 255) String remark) { }
    public record Comment(@NotBlank @Size(max = 500) String content) { }
    public record Share(@Min(1) @Max(168) int hours, @Min(1) @Max(1000) int maxAccess) { }
    public record Settings(@NotBlank @Size(max = 60) String name) { }
    public record MemberRole(@NotBlank @Pattern(regexp = "admin|member") String role) { }
    public record Profile(@NotBlank @Size(max = 40) String nickname) { }
    public record Phone(@NotBlank @Pattern(regexp = "1[0-9]{10}") String phone,
                        @NotBlank @Size(max = 72) String currentPassword) { }
    public record Password(@NotBlank @Size(max = 72) String currentPassword,
                           @NotBlank @Size(min = 8, max = 60) String newPassword,
                           @NotBlank @Size(min = 8, max = 60) String confirmPassword) { }
}
