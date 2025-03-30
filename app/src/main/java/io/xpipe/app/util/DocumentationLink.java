package io.xpipe.app.util;

public enum DocumentationLink {
    INDEX(""),
    TTY("troubleshoot/tty"),
    WINDOWS_SSH("troubleshoot/windows-ssh"),
    MACOS_SETUP("guide/installation#macos"),
    SSH_AGENT("troubleshoot/ssh-agent-socket"),
    DOUBLE_PROMPT("troubleshoot/two-step-connections"),
    LICENSE_ACTIVATION("troubleshoot/license-activation"),
    PRIVACY("legal/privacy"),
    EULA("legal/eula"),
    WEBTOP_UPDATE("guide/webtop#updating"),
    SYNC("guide/sync"),
    SCRIPTING("guide/scripting"),
    KEEPASSXC("guide/keepassxc"),
    SSH("guide/ssh");

    private final String page;

    DocumentationLink(String page) {
        this.page = page;
    }

    public void open() {
        Hyperlinks.open("https://docs.xpipe.io/" + page);
    }

    public String getLink() {
        return "https://docs.xpipe.io/" + page;
    }
}
