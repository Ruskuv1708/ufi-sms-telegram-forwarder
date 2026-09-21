package com.ufi.smsforwarder;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

final class TlsFactory extends SSLSocketFactory {
    private static volatile SSLSocketFactory instance;
    private final SSLSocketFactory delegate;

    private TlsFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    static SSLSocketFactory get(Context context) throws Exception {
        SSLSocketFactory local = instance;
        if (local != null) {
            return local;
        }
        synchronized (TlsFactory.class) {
            if (instance == null) {
                CertificateFactory certificates = CertificateFactory.getInstance("X.509");
                InputStream stream = context.getAssets().open("godaddy-root-g2.pem");
                Certificate root;
                try {
                    root = certificates.generateCertificate(stream);
                } finally {
                    stream.close();
                }

                KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                store.setCertificateEntry("telegram-root", root);

                TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm()
                );
                trustManagers.init(store);

                SSLContext ssl = SSLContext.getInstance("TLSv1.2");
                ssl.init(null, trustManagers.getTrustManagers(), null);
                instance = new TlsFactory(ssl.getSocketFactory());
            }
            return instance;
        }
    }

    private Socket enableTls12(Socket socket) {
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
        }
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return enableTls12(delegate.createSocket(socket, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return enableTls12(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return enableTls12(delegate.createSocket(address, port, localAddress, localPort));
    }
}
