package in.hisaab.users;

import in.hisaab.users.dto.MeResponse;
import in.hisaab.users.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public MeResponse me() {
    return userService.me();
  }

  @PatchMapping
  public MeResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
    return userService.updateSettings(request);
  }
}
