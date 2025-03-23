package io.xpipe.app.browser;

import io.xpipe.app.browser.file.BrowserConnectionListComp;
import io.xpipe.app.browser.file.BrowserConnectionListFilterComp;
import io.xpipe.app.browser.file.BrowserEntry;
import io.xpipe.app.browser.file.BrowserFileSystemTabComp;
import io.xpipe.app.browser.file.BrowserFileSystemTabModel;
import io.xpipe.app.comp.Comp;
import io.xpipe.app.comp.base.DialogComp;
import io.xpipe.app.comp.base.LeftSplitPaneComp;
import io.xpipe.app.comp.base.StackComp;
import io.xpipe.app.comp.base.VerticalComp;
import io.xpipe.app.comp.store.StoreEntryWrapper;
import io.xpipe.app.core.AppLayoutModel;
import io.xpipe.app.ext.ShellStore;
import io.xpipe.app.storage.DataStoreEntryRef;
import io.xpipe.app.util.BindingsHelper;
import io.xpipe.app.util.FileReference;
import io.xpipe.app.util.PlatformThread;
import io.xpipe.app.util.ThreadHelper;
import io.xpipe.core.store.FileSystemStore;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BrowserFileChooserSessionComp extends DialogComp {

    private final Stage stage;
    private final BrowserFileChooserSessionModel model;

    public BrowserFileChooserSessionComp(Stage stage, BrowserFileChooserSessionModel model) {
        this.stage = stage;
        this.model = model;
    }

    public static void openSingleFile(
            Supplier<DataStoreEntryRef<? extends FileSystemStore>> store, Consumer<FileReference> file, boolean save) {
        PlatformThread.runLaterIfNeeded(() -> {
            var lastWindow = Window.getWindows().stream()
                    .filter(window -> window.isFocused())
                    .findFirst();
            var model = new BrowserFileChooserSessionModel(BrowserFileSystemTabModel.SelectionMode.SINGLE_FILE);
            DialogComp.showWindow(save ? "saveFileTitle" : "openFileTitle", stage -> {
                stage.addEventFilter(WindowEvent.WINDOW_HIDDEN, event -> {
                    lastWindow.ifPresent(window -> window.requestFocus());
                });
                var comp = new BrowserFileChooserSessionComp(stage, model);
                comp.apply(struc -> struc.get().setPrefSize(1200, 700))
                        .styleClass("browser")
                        .styleClass("chooser");
                return comp;
            });
            model.setOnFinish(fileStores -> {
                file.accept(fileStores.size() > 0 ? fileStores.getFirst() : null);
            });
            ThreadHelper.runAsync(() -> {
                model.openFileSystemAsync(store.get(), null, null);
            });
        });
    }

    @Override
    protected String finishKey() {
        return "select";
    }

    @Override
    protected Comp<?> pane(Comp<?> content) {
        return content;
    }

    @Override
    protected void finish() {
        stage.close();
        model.finishChooser();
    }

    @Override
    protected void discard() {
        model.finishWithoutChoice();
    }

    @Override
    public Comp<?> content() {
        Predicate<StoreEntryWrapper> applicable = storeEntryWrapper -> {
            return (storeEntryWrapper.getEntry().getStore() instanceof ShellStore)
                    && storeEntryWrapper.getEntry().getValidity().isUsable();
        };
        BiConsumer<StoreEntryWrapper, BooleanProperty> action = (w, busy) -> {
            ThreadHelper.runFailableAsync(() -> {
                var entry = w.getEntry();
                if (!entry.getValidity().isUsable()) {
                    return;
                }

                // Don't open same system again
                var current = model.getSelectedEntry().getValue();
                if (current != null && entry.ref().equals(current.getEntry())) {
                    return;
                }

                if (entry.getStore() instanceof ShellStore) {
                    model.openFileSystemAsync(entry.ref(), null, busy);
                }
            });
        };

        var bookmarkTopBar = new BrowserConnectionListFilterComp();
        var bookmarksList = new BrowserConnectionListComp(
                BindingsHelper.map(
                        model.getSelectedEntry(), v -> v != null ? v.getEntry().get() : null),
                applicable,
                action,
                bookmarkTopBar.getCategory(),
                bookmarkTopBar.getFilter());
        var bookmarksContainer = new StackComp(List.of(bookmarksList)).styleClass("bookmarks-container");
        bookmarksContainer
                .apply(struc -> {
                    var rec = new Rectangle();
                    rec.widthProperty().bind(struc.get().widthProperty());
                    rec.heightProperty().bind(struc.get().heightProperty());
                    rec.setArcHeight(7);
                    rec.setArcWidth(7);
                    struc.get().getChildren().getFirst().setClip(rec);
                })
                .vgrow();

        var stack = Comp.of(() -> {
            var s = new StackPane();
            model.getSelectedEntry().subscribe(selected -> {
                PlatformThread.runLaterIfNeeded(() -> {
                    if (selected != null) {
                        s.getChildren().setAll(new BrowserFileSystemTabComp(selected, false).createRegion());
                    } else {
                        s.getChildren().clear();
                    }
                });
            });
            return s;
        });

        var vertical = new VerticalComp(List.of(bookmarkTopBar, bookmarksContainer)).styleClass("left");
        var splitPane = new LeftSplitPaneComp(vertical, stack)
                .withInitialWidth(AppLayoutModel.get().getSavedState().getBrowserConnectionsWidth())
                .withOnDividerChange(AppLayoutModel.get().getSavedState()::setBrowserConnectionsWidth)
                .styleClass("background")
                .apply(struc -> {
                    struc.getLeft().setMinWidth(200);
                    struc.getLeft().setMaxWidth(500);
                });
        return splitPane;
    }

    @Override
    public Comp<?> bottom() {
        return Comp.of(() -> {
            var selected = new HBox();
            selected.setAlignment(Pos.CENTER_LEFT);
            model.getFileSelection().addListener((ListChangeListener<? super BrowserEntry>) c -> {
                PlatformThread.runLaterIfNeeded(() -> {
                    selected.getChildren()
                            .setAll(c.getList().stream()
                                    .map(s -> {
                                        var field = new TextField(
                                                s.getRawFileEntry().getPath().toString());
                                        field.setEditable(false);
                                        field.getStyleClass().add("chooser-selection");
                                        HBox.setHgrow(field, Priority.ALWAYS);
                                        return field;
                                    })
                                    .toList());
                });
            });
            var bottomBar = new HBox(selected);
            HBox.setHgrow(selected, Priority.ALWAYS);
            bottomBar.setAlignment(Pos.CENTER);
            return bottomBar;
        });
    }
}
