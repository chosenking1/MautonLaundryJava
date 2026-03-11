package com.work.mautonlaundry.controllers;

import com.work.mautonlaundry.data.model.LaundryItemCatalog;
import com.work.mautonlaundry.dtos.requests.laundryitemrequests.LaundryItemRequest;
import com.work.mautonlaundry.dtos.responses.common.MessageResponse;
import com.work.mautonlaundry.services.LaundryItemCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/laundry-items")
@RequiredArgsConstructor
public class LaundryItemCatalogController {
    
    private final LaundryItemCatalogService catalogService;

    @GetMapping
    @PreAuthorize("hasAuthority('LAUNDRY_ITEM_READ')")
    public ResponseEntity<List<LaundryItemCatalog>> getAllItems() {
        List<LaundryItemCatalog> items = catalogService.getAllActiveItems();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LAUNDRY_ITEM_READ')")
    public ResponseEntity<LaundryItemCatalog> getItemById(@PathVariable Long id) {
        LaundryItemCatalog item = catalogService.getItemById(id);
        return ResponseEntity.ok(item);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LAUNDRY_ITEM_CREATE')")
    public ResponseEntity<LaundryItemCatalog> createItem(@Valid @RequestBody LaundryItemRequest request) {
        LaundryItemCatalog item = catalogService.createItem(request);
        return new ResponseEntity<>(item, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LAUNDRY_ITEM_UPDATE')")
    public ResponseEntity<LaundryItemCatalog> updateItem(
            @PathVariable Long id, 
            @Valid @RequestBody LaundryItemRequest request) {
        LaundryItemCatalog item = catalogService.updateItem(id, request);
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LAUNDRY_ITEM_DELETE')")
    public ResponseEntity<MessageResponse> deactivateItem(@PathVariable Long id) {
        catalogService.deactivateItem(id);
        return ResponseEntity.ok(new MessageResponse("Item deactivated successfully"));
    }
}
