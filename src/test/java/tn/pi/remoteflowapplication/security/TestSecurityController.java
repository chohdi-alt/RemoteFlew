package tn.pi.remoteflowapplication.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestSecurityController {

    @GetMapping("/api/admin/test")
    public ResponseEntity<String> admin() {
        return ResponseEntity.ok("Admin Access");
    }

    @GetMapping("/api/manager/test")
    public ResponseEntity<String> manager() {
        return ResponseEntity.ok("Manager Access");
    }

    @GetMapping("/api/hr/test")
    public ResponseEntity<String> hr() {
        return ResponseEntity.ok("HR Access");
    }

    @GetMapping("/api/employee/test")
    public ResponseEntity<String> employee() {
        return ResponseEntity.ok("Employee Access");
    }
}
