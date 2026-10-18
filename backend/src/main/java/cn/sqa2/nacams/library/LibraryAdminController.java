package cn.sqa2.nacams.library;

import cn.sqa2.nacams.auth.Access;
import cn.sqa2.nacams.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class LibraryAdminController {
    private final Access access;
    private final BorrowService borrows;
    private final BookService books;
    private final LibraryConfigService config;
    private final DownloadService downloads;

    public LibraryAdminController(Access access, BorrowService borrows, BookService books,
                                  LibraryConfigService config, DownloadService downloads) {
        this.access = access; this.borrows = borrows; this.books = books; this.config = config; this.downloads = downloads;
    }

    @GetMapping("/admin/borrow-requests")
    public Api<?> requests(@RequestParam(defaultValue = "pending") String status,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(name = "page_size", defaultValue = "20") int size) {
        access.librarian();
        return Api.ok(borrows.requests(status, page, size));
    }

    @PostMapping("/admin/borrow-requests/{id}/approve")
    public Api<?> approve(@PathVariable long id) {
        return Api.ok(borrows.approve(id, access.librarian().id()));
    }

    @PostMapping("/admin/borrow-requests/{id}/reject")
    public Api<?> reject(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        var librarian = access.librarian();
        String reason = body == null ? "" : Db.str(body, "reason");
        return Api.ok(borrows.reject(id, reason, librarian.id()));
    }

    @GetMapping("/admin/borrows")
    public Api<?> borrows(@RequestParam(defaultValue = "") String status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(name = "page_size", defaultValue = "20") int size) {
        access.librarian();
        return Api.ok(borrows.borrows(status, page, size));
    }

    @PostMapping("/admin/borrows/{id}/return")
    public Api<?> returnBook(@PathVariable long id) {
        return Api.ok(borrows.returnBook(id, access.librarian().id()));
    }

    @GetMapping("/admin/library/config")
    public Api<?> config() {
        access.librarian();
        return Api.ok(config.view(config.get()));
    }

    @PutMapping("/admin/library/config")
    public Api<?> updateConfig(@RequestBody Map<String, Object> body) {
        access.librarian();
        return Api.ok(config.update(body));
    }

    @PostMapping("/admin/books")
    public Api<?> createBook(@RequestBody Map<String, Object> body) {
        access.librarian();
        return Api.ok(books.create(body));
    }

    @PutMapping("/admin/books/{id}")
    public Api<?> updateBook(@PathVariable long id, @RequestBody Map<String, Object> body) {
        access.librarian();
        return Api.ok(books.update(id, body));
    }

    @DeleteMapping("/admin/books/{id}")
    public Api<?> deleteBook(@PathVariable long id) {
        access.librarian();
        books.delete(id);
        return Api.ok(null);
    }

    @PostMapping("/admin/books/import")
    public Api<?> importBooks(@RequestParam MultipartFile file,
                              @RequestParam(required = false) Integer purchaseYear) {
        access.librarian();
        return Api.ok(books.importBooks(file, purchaseYear));
    }

    @GetMapping("/admin/violations")
    public Api<?> violations(@RequestParam(required = false) Long userId,
                             @RequestParam(defaultValue = "") String status,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(name = "page_size", defaultValue = "20") int size) {
        access.librarian();
        return Api.ok(borrows.violations(userId, status, page, size));
    }

    @PostMapping("/admin/violations")
    public Api<?> confirmViolation(@RequestBody Map<String, Object> body) {
        var librarian = access.librarian();
        Object id = body.get("id");
        if (!(id instanceof Number number)) throw new ApiException(422, "id必填且为违规候选主键");
        return Api.ok(borrows.confirmViolation(number.longValue(), librarian.id()));
    }

    @PostMapping("/admin/downloads")
    public Api<?> createDownload(@RequestParam String category, @RequestParam String title,
                                 @RequestParam(name = "sortOrder", defaultValue = "0") int sortOrder,
                                 @RequestParam(required = false) MultipartFile file) {
        access.admin();
        return Api.ok(downloads.create(category, title, sortOrder, file));
    }

    @PutMapping("/admin/downloads/{id}")
    public Api<?> updateDownload(@PathVariable long id,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(name = "sortOrder", required = false) Integer sortOrder,
                                 @RequestParam(required = false) MultipartFile file) {
        access.admin();
        return Api.ok(downloads.update(id, category, title, sortOrder, file));
    }

    @DeleteMapping("/admin/downloads/{id}")
    public Api<?> deleteDownload(@PathVariable long id) {
        access.admin();
        downloads.delete(id);
        return Api.ok(null);
    }
}