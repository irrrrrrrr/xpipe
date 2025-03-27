package io.xpipe.app.comp.base;

import atlantafx.base.controls.Spacer;
import io.xpipe.app.comp.Comp;
import io.xpipe.app.comp.SimpleComp;
import io.xpipe.app.core.AppFontSizes;
import io.xpipe.app.core.AppI18n;
import io.xpipe.app.core.AppLogs;
import io.xpipe.app.util.PlatformThread;
import io.xpipe.core.process.OsType;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.layout.ModalBox;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;

public class ModalOverlayComp extends SimpleComp {

    private final Comp<?> background;
    private final Property<ModalOverlay> overlayContent;

    public ModalOverlayComp(Comp<?> background, Property<ModalOverlay> overlayContent) {
        this.background = background;
        this.overlayContent = overlayContent;
    }

    private Animation showAnimation(Node node) {
        if (OsType.getLocal() == OsType.LINUX) {
            return null;
        }

        Timeline t = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(node.opacityProperty(), 0.01)),
                new KeyFrame(Duration.millis(100), new KeyValue(node.opacityProperty(), 0.01)),
                new KeyFrame(Duration.millis(200), new KeyValue(node.opacityProperty(), 1)));
        t.statusProperty().addListener((obs, old, val) -> {
            if (val == Animation.Status.STOPPED) {
                node.setOpacity(1.0F);
            }
        });
        return t;
    }

    @Override
    protected Region createSimple() {
        var bgRegion = background.createRegion();
        var modal = new ModalPane();
        modal.setInTransitionFactory(null);
        modal.setOutTransitionFactory(
                OsType.getLocal() == OsType.LINUX ? null : node -> Animations.fadeOut(node, Duration.millis(50)));
        modal.focusedProperty().addListener((observable, oldValue, newValue) -> {
            var c = modal.getContent();
            if (newValue && c != null) {
                c.requestFocus();
            }
        });
        modal.getStyleClass().add("modal-overlay-comp");
        var pane = new StackPane(bgRegion, modal);
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setPickOnBounds(false);
        pane.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                if (modal.isDisplay()) {
                    modal.requestFocus();
                } else {
                    bgRegion.requestFocus();
                }
            }
        });

        modal.contentProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                overlayContent.setValue(null);
                bgRegion.setDisable(false);
            }

            if (newValue != null) {
                bgRegion.setDisable(true);
            }
        });

        modal.displayProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                overlayContent.setValue(null);
                bgRegion.setDisable(false);
            }
        });

        modal.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                var ov = overlayContent.getValue();
                if (ov != null) {
                    var def = ov.getButtons().stream()
                            .filter(modalButton -> modalButton instanceof ModalButton mb && mb.isDefaultButton())
                            .findFirst();
                    if (def.isPresent()) {
                        var mb = (ModalButton) def.get();
                        if (mb.getAction() != null) {
                            mb.getAction().run();
                        }
                        if (mb.isClose()) {
                            overlayContent.setValue(null);
                        }
                        event.consume();
                    }
                }
            }
        });

        overlayContent.addListener((observable, oldValue, newValue) -> {
            PlatformThread.runLaterIfNeeded(() -> {
                if (oldValue != null && modal.isDisplay()) {
                    if (newValue == null) {
                        modal.hide(false);
                    }
                }

                if (oldValue != null) {
                    if (oldValue.getContent() instanceof ModalOverlayContentComp mocc) {
                        mocc.onClose();
                    }
                    if (oldValue.getContent() instanceof ModalOverlayContentComp mocc) {
                        mocc.setModalOverlay(null);
                    }
                }

                try {
                    if (newValue != null) {
                        if (newValue.getContent() instanceof ModalOverlayContentComp mocc) {
                            mocc.setModalOverlay(newValue);
                        }
                        showModalBox(modal, newValue);
                    }
                } catch (Throwable t) {
                    AppLogs.get().logException(null, t);
                    Platform.runLater(() -> {
                        overlayContent.setValue(null);
                    });
                }
            });
        });

        var current = overlayContent.getValue();
        if (current != null) {
            showModalBox(modal, current);
        }

        return pane;
    }

    private void showModalBox(ModalPane modal, ModalOverlay overlay) {
        var modalBox = toBox(modal, overlay);
        modalBox.setOpacity(0.01);
        modal.setPersistent(overlay.isPersistent());
        modal.show(modalBox);
        if (overlay.isPersistent() || overlay.getTitleKey() == null) {
            var closeButton = modalBox.lookup(".close-button");
            if (closeButton != null) {
                closeButton.setVisible(false);
            }
        }

        // This is ugly, but works better than animations
        // The content layout takes some time, resulting in shifting content
        // We don't want to show that, so wait after that is done
        Platform.runLater(() -> {
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    Platform.runLater(() -> {
                        modalBox.setOpacity(1.0);
                    });
                });
            });
        });
    }

    private Region toBox(ModalPane pane, ModalOverlay newValue) {
        Region r = newValue.getContent().createRegion();

        var content = new VBox(r);
        content.focusedProperty().addListener((o, old, n) -> {
            if (n) {
                r.requestFocus();
            }
        });
        content.setSpacing(25);
        content.setPadding(new Insets(13, 27, 20, 27));

        if (newValue.getTitleKey() != null) {
            var l = new Label(
                    AppI18n.get(newValue.getTitleKey()),
                    newValue.getGraphic() != null ? newValue.getGraphic().createGraphicNode() : null);
            l.setGraphicTextGap(8);
            AppFontSizes.xl(l);
            content.getChildren().addFirst(l);
        } else {
            content.getChildren().addFirst(Comp.vspacer(0).createRegion());
        }

        if (newValue.getButtons().size() > 0) {
            var buttonBar = new HBox();
            buttonBar.setSpacing(10);
            buttonBar.setAlignment(Pos.CENTER_RIGHT);
            for (var o : newValue.getButtons()) {
                var node = o instanceof ModalButton mb ? toButton(mb) : ((Comp<?>) o).createRegion();
                buttonBar.getChildren().add(node);
                if (o instanceof ModalButton) {
                    node.prefHeightProperty().bind(buttonBar.heightProperty());
                }
            }
            content.getChildren().add(buttonBar);
            AppFontSizes.xs(buttonBar);
        }

        var modalBox = new ModalBox(pane, content) {

            @Override
            protected void setCloseButtonPosition() {
                setTopAnchor(closeButton, 10d);
                setRightAnchor(closeButton, 19d);
            }
        };
        if (newValue.getHideAction() != null) {
            modalBox.setOnMinimize(event -> {
                newValue.getHideAction().run();
                event.consume();
            });
        }
        modalBox.setOnClose(event -> {
            overlayContent.setValue(null);
            event.consume();
        });
        r.maxHeightProperty().bind(pane.heightProperty().subtract(200));

        content.prefWidthProperty().bind(modalBox.widthProperty());
        modalBox.setMinWidth(100);
        modalBox.setMinHeight(100);
        modalBox.prefWidthProperty().bind(modalBoxWidth(pane, r));
        modalBox.maxWidthProperty().bind(modalBox.prefWidthProperty());
        modalBox.prefHeightProperty().bind(modalBoxHeight(pane, content));
        modalBox.setMaxHeight(Region.USE_PREF_SIZE);
        modalBox.focusedProperty().addListener((o, old, n) -> {
            if (n) {
                content.requestFocus();
            }
        });

        if (newValue.getContent() instanceof ModalOverlayContentComp mocc) {
            var busy = mocc.busy();
            if (busy != null) {
                var loading = LoadingOverlayComp.noProgress(Comp.of(() -> modalBox), busy);
                //                loading.apply(struc -> {
                //                    var bg = struc.get().getChildren().getFirst();
                //                    struc.get().getChildren().get(1).addEventFilter(MouseEvent.MOUSE_PRESSED, event ->
                // {
                //                        bg.fireEvent(event);
                //                        event.consume();
                //                    });
                //                });
                return loading.createRegion();
            }
        }

        return modalBox;
    }

    private ObservableDoubleValue modalBoxWidth(ModalPane pane, Region r) {
        return Bindings.createDoubleBinding(
                () -> {
                    var max = pane.getWidth() - 50;
                    if (r.getPrefWidth() != Region.USE_COMPUTED_SIZE) {
                        return Math.min(max, r.getPrefWidth() + 50);
                    }
                    return max;
                },
                pane.widthProperty(),
                r.prefWidthProperty());
    }

    private ObservableDoubleValue modalBoxHeight(ModalPane pane, Region content) {
        return Bindings.createDoubleBinding(
                () -> {
                    var max = pane.getHeight() - 20;
                    if (content.getPrefHeight() != Region.USE_COMPUTED_SIZE) {
                        return Math.min(max, content.getPrefHeight());
                    }

                    return Math.min(max, content.getHeight());
                },
                pane.heightProperty(),
                pane.prefHeightProperty(),
                content.prefHeightProperty(),
                content.heightProperty(),
                content.maxHeightProperty());
    }

    private Button toButton(ModalButton mb) {
        var button = new Button(mb.getKey() != null ? AppI18n.get(mb.getKey()) : null);
        if (mb.isDefaultButton()) {
            button.getStyleClass().add(Styles.ACCENT);
        }
        if (mb.getAugment() != null) {
            mb.getAugment().accept(button);
        }
        button.managedProperty().bind(button.visibleProperty());
        button.setOnAction(event -> {
            if (mb.getAction() != null) {
                mb.getAction().run();
            }
            if (mb.isClose()) {
                overlayContent.setValue(null);
            }
            event.consume();
        });
        return button;
    }

    private Timeline fadeInDelyed(Node node) {
        var t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.opacityProperty(), 0.01)),
                new KeyFrame(Duration.millis(50), new KeyValue(node.opacityProperty(), 0.01, Animations.EASE)),
                new KeyFrame(Duration.millis(1250), new KeyValue(node.opacityProperty(), 1, Animations.EASE)));

        t.statusProperty().addListener((obs, old, val) -> {
            if (val == Animation.Status.STOPPED) {
                node.setOpacity(1);
            }
        });

        return t;
    }
}
