package org.elasticmq.marshalling

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Copied and converted from JGroups.
 *
 * Extends ByteArrayOutputStream, but exposes the internal buffer. This way we don't need to call
 * toByteArray() which copies the internal buffer
 * @author Bela Ban
 */
private[marshalling] class ExposedByteArrayOutputStream(initialSize: Int) extends ByteArrayOutputStream(initialSize) {
  /**
   * Resets count and creates a new buf if the current buf is > max_size. This method is not synchronized
   */
  def reset(max_size: Int) {
    reset()
    if (buf == null || buf.length > max_size) {
      buf = new Array[Byte](max_size)
    }
  }

  def getRawBuffer: Array[Byte] = buf

  def getCapacity: Int = buf.length

  override def write(b: Int) {
    var newcount: Int = count + 1
    if (newcount > buf.length) {
      var newbuf = new Array[Byte](Math.max(buf.length << 1, newcount))
      System.arraycopy(buf, 0, newbuf, 0, count)
      buf = newbuf
    }
    buf(count) = b.asInstanceOf[Byte]
    count = newcount
  }

  /**
   * Writes <code>len</code> bytes from the specified byte array
   * starting at offset <code>off</code> to this byte array output stream.
   * @param b   the data.
   * @param off the start offset in the data.
   * @param len the number of bytes to write.
   */
  override def write(b: Array[Byte], off: Int, len: Int) {
    if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException
    }
    else if (len == 0) {
      return
    }
    var newcount: Int = count + len
    if (newcount > buf.length) {
      var newbuf = new Array[Byte](Math.max(buf.length << 1, newcount))
      System.arraycopy(buf, 0, newbuf, 0, count)
      buf = newbuf
    }
    System.arraycopy(b, off, buf, count, len)
    count = newcount
  }

  /**
   * Writes the complete contents of this byte array output stream to
   * the specified output stream argument, as if by calling the output
   * stream's write method using <code>out.write(buf, 0, count)</code>.
   * @param out the output stream to which to write the data.
   * @throws java.io.IOException if an I/O error occurs.
   */
  override def writeTo(out: OutputStream) {
    out.write(buf, 0, count)
  }

  /**
   * Resets the <code>count</code> field of this byte array output
   * stream to zero, so that all currently accumulated output in the
   * output stream is discarded. The output stream can be used again,
   * reusing the already allocated buffer space.
   * @see java.io.ByteArrayInputStream#count
   */
  override def reset() {
    count = 0
  }

  /**
   * Creates a newly allocated byte array. Its size is the current
   * size of this output stream and the valid contents of the buffer
   * have been copied into it.
   * @return the current contents of this output stream, as a byte array.
   * @see java.io.ByteArrayOutputStream#size()
   */
  override def toByteArray(): Array[Byte] = {
    var newbuf = new Array[Byte](count)
    System.arraycopy(buf, 0, newbuf, 0, count)
    newbuf
  }

  /**
   * Returns the current size of the buffer.
   * @return the value of the <code>count</code> field, which is the number
   *         of valid bytes in this output stream.
   * @see java.io.ByteArrayOutputStream#count
   */
  override def size: Int = count

  /**
   * Converts the buffer's contents into a string decoding bytes using the
   * platform's default character set. The length of the new <tt>String</tt>
   * is a function of the character set, and hence may not be equal to the
   * size of the buffer.
   * <p/>
   * <p> This method always replaces malformed-input and unmappable-character
   * sequences with the default replacement string for the platform's
   * default character set. The {@linkplain java.nio.charset.CharsetDecoder}
   * class should be used when more control over the decoding process is
   * required.
   * @return String decoded from the buffer's contents.
   * @since JDK1.1
   */
  override def toString: String = new String(buf, 0, count)

  /**
   * Converts the buffer's contents into a string by decoding the bytes using
   * the specified {@link java.nio.charset.Charset charsetName}. The length of
   * the new <tt>String</tt> is a function of the charset, and hence may not be
   * equal to the length of the byte array.
   * <p/>
   * <p> This method always replaces malformed-input and unmappable-character
   * sequences with this charset's default replacement string. The {@link
   * java.nio.charset.CharsetDecoder} class should be used when more control
   * over the decoding process is required.
   * @param charsetName the name of a supported
   *                    { @linkplain java.nio.charset.Charset </code>charset<code>}
   * @return String decoded from the buffer's contents.
   * @throws java.io.UnsupportedEncodingException
     * If the named charset is not supported
   * @since JDK1.1
   */
  override def toString(charsetName: String): String = new String(buf, 0, count, charsetName)
}