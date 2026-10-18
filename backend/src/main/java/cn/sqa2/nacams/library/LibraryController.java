package cn.sqa2.nacams.library;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class LibraryController {
    private final Access access;
    private final BorrowService borrows;
    private final NotificationService notifications;
    private final DownloadService downloads;
    private final HelpService help;

    public LibraryController(Access access, BorrowService borrows, NotificationService notifications,
                             DownloadService downloads, HelpService help) {
        this.access = access; this.borrows = borrows; this.notifications = notifications;
        this.downloads = downloads; this.help = help;
    }

    @GetMapping("/books")
    public Api<?> books(@RequestParam(defaultValue = "") String keyword,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(name = "page_size", defaultValue = "20") int size) {
        access.user();
        return Api.ok(borrows.books(keyword, page, size));
    }

    @PostMapping("/books/{id}/borrow-requests")
    public Api<?> createRequest(@PathVariable long id) {
        var user = access.user();
        return Api.ok(borrows.request(user.id(), id));
    }

    @GetMapping("/me/borrows")
    public Api<?> myBorrows(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(name = "page_size", defaultValue = "20") int size) {
        return Api.ok(borrows.myBorrows(access.user().id(), page, size));
    }

    @GetMapping("/me/violations")
    public Api<?> myViolations(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(name = "page_size", defaultValue = "20") int size) {
        return Api.ok(borrows.myViolations(access.user().id(), page, size));
    }

    @GetMapping("/notifications")
    public Api<?> notifications(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(name = "page_size", defaultValue = "20") int size) {
        return Api.ok(notifications.list(access.user().id(), page, size));
    }

    @PutMapping("/notifications/{id}/read")
    public Api<?> read(@PathVariable long id) {
        notifications.markRead(id, access.user().id());
        return Api.ok(null);
    }

    @GetMapping("/downloads")
    public Api<?> downloads(@RequestParam(defaultValue = "") String category,
                            @RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(name = "page_size", defaultValue = "20") int size) {
        access.user();
        return Api.ok(downloads.list(category, keyword, page, size));
    }

    @GetMapping("/downloads/{id}/file")
    public ResponseEntity<?> downloadFile(@PathVariable long id) {
        access.user();
        return downloads.file(id);
    }

    @GetMapping("/help")
    public Api<?> help(@RequestParam(defaultValue = "") String keyword) {
        access.user();
        return Api.ok(help.search(keyword));
    }

}