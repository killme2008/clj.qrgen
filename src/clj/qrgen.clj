(ns clj.qrgen
  "A Clojure library designed to generate QRCode wrapped java [QRGen](https://github.com/kenglxn/QRGen)."
  {:author "dennis <killme2008@gmail.com>"}
  (:import [net.glxn.qrgen.vcard VCard]
           [net.glxn.qrgen.image ImageType]
           [com.google.zxing.qrcode.decoder ErrorCorrectionLevel]
           [com.google.zxing EncodeHintType]
           [java.io BufferedOutputStream ByteArrayOutputStream]
           [java.util EnumSet]
           [net.glxn.qrgen QRCode])
  (:use [clojure.java.io :only [Coercions IOFactory default-streams-impl]]))

(defmacro define-constants [enum]
  `(do ~@
     (map (fn [v]
            `(def ~(symbol (.name v)) ~(str enum "/" (.name v))  ~v)) (EnumSet/allOf (resolve enum)))))

(define-constants ImageType)
(define-constants ErrorCorrectionLevel)
(define-constants EncodeHintType)

(defprotocol QRCodeGenerator

  (^QRCode qrcode-from [this]))

(extend-type String
  QRCodeGenerator
  (qrcode-from [this] (QRCode/from ^String this)))

(extend-type VCard
  QRCodeGenerator
  (qrcode-from [this] (QRCode/from ^VCard this)))

(defn- with-size [^QRCode qc size]
  (.withSize qc (first size) (second size)))

(defn- with-hint [^QRCode qc hint]
  (.withHint qc (ffirst hint) (second (first hint))))

(defmacro ^:private
  invoke-when [^QRCode qc method v]
  `(when ~v
    (~method ~qc ~v)))

(defn make-qrcode [^QRCode qc & {:keys [image-type size charset correction hint]}]
  (doto
      qc
    (invoke-when .to image-type)
    (invoke-when with-size size)
    (invoke-when .withCharset charset)
    (invoke-when .withErrorCorrection correction)
    (invoke-when with-hint hint)))

(defn ^QRCode from
  "Create a QR code from the given text or VCard with supplied options.
     Valid options include:
        :image-type -- image type, such as JPG,GIF or PNG;
        :size -- a vector for image size that is in the form of [width height], default is [125 125];
        :charset -- character set string,such as \"UTF-8\" etc;
        :correction -- error correction level, it can be L,M,Q or H;
        :hint -- write hint, it can be ERROR_CORRECTION,CHARACTER_SET,MARGIN,PDF417_COMPACT,PDF417_COMPACTION or PDF417_DIMENSIONS. "
  [source & opts]
  (apply make-qrcode (qrcode-from source) opts))

(defn ^VCard vcard
  "Created a vCard data with options, valid options include:
    :company
    :title
    :phonenumber
    :email
    :address
    :website"
  [^String name & {:keys [company title phonenumber email address website]}]
  (doto
      (VCard. name)
    (invoke-when .setCompany company)
    (invoke-when .setTitle title)
    (invoke-when .setPhonenumber phonenumber)
    (invoke-when .setEmail email)
    (invoke-when .setAddress address)
    (invoke-when .setWebsite website)))

(defn as-file
  "Returns a java.io.File representation of the QR code. The file is set to be deleted on exit.
   If you want the file to live beyond the life of the jvm process, you should make a copy."
  ([^QRCode qc]
     (.file qc))
  ([^QRCode qc ^String name]
     (.file qc name)))

(defn as-stream
  "Returns a java.io.ByteArrayOutputStream representation of the QR code."
  [^QRCode qc]
  (.stream qc))

(defn as-bytes [^QRCode qc]
  (.toByteArray ^ByteArrayOutputStream (as-stream qc)))

;; support clojure.java.io/file and clojure.java.io/output-stream
(extend-protocol Coercions
  QRCode
  (as-file [qc] (.file qc))
  (as-url [qc] (.toURL (.toURI (.file qc)))))

(extend QRCode
  IOFactory
  (assoc default-streams-impl
    :make-output-stream (fn [qc opts] (BufferedOutputStream. (.stream qc)))))
