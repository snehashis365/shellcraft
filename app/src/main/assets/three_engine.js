// Offline WebGL2 / Three.js-style 3D wireframe & particle core for ShellCraft Hero visuals.
// Self-contained (zero CDN dependency), supports interactive pointer orbit + state transitions.
(function (global) {
  function hexToRgbNorm(hex) {
    var clean = (hex || '#00e5ff').replace('#', '');
    if (clean.length === 3) {
      clean = clean[0]+clean[0]+clean[1]+clean[1]+clean[2]+clean[2];
    }
    var n = parseInt(clean, 16) || 0x00e5ff;
    return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
  }

  function lerp(a, b, t) { return a + (b - a) * t; }

  function createIcosahedronEdges(radius) {
    var t = (1.0 + Math.sqrt(5.0)) / 2.0;
    var raw = [
      [-1, t, 0], [1, t, 0], [-1, -t, 0], [1, -t, 0],
      [0, -1, t], [0, 1, t], [0, -1, -t], [0, 1, -t],
      [t, 0, -1], [t, 0, 1], [-t, 0, -1], [-t, 0, 1]
    ];
    var verts = raw.map(function (v) {
      var l = Math.hypot(v[0], v[1], v[2]);
      return [v[0] / l * radius, v[1] / l * radius, v[2] / l * radius];
    });
    var faces = [
      [0,11,5],[0,5,1],[0,1,7],[0,7,10],[0,10,11],
      [1,5,9],[5,11,4],[11,10,2],[10,7,6],[7,1,8],
      [3,9,4],[3,4,2],[3,2,6],[3,6,8],[3,8,9],
      [4,9,5],[2,4,11],[6,2,10],[8,6,7],[9,8,1]
    ];
    var seen = {};
    var pts = [];
    faces.forEach(function (f) {
      for (var i = 0; i < 3; i++) {
        var a = f[i], b = f[(i + 1) % 3];
        var key = a < b ? a + ':' + b : b + ':' + a;
        if (!seen[key]) {
          seen[key] = true;
          pts.push(verts[a][0], verts[a][1], verts[a][2]);
          pts.push(verts[b][0], verts[b][1], verts[b][2]);
        }
      }
    });
    return new Float32Array(pts);
  }

  function createRingEdges(radius, segments, tiltX, tiltZ) {
    var pts = [];
    var cx = Math.cos(tiltX), sx = Math.sin(tiltX);
    var cz = Math.cos(tiltZ), sz = Math.sin(tiltZ);
    function transform(x, y, z) {
      var y1 = y * cx - z * sx;
      var z1 = y * sx + z * cx;
      var x2 = x * cz - y1 * sz;
      var y2 = x * sz + y1 * cz;
      return [x2, y2, z1];
    }
    for (var i = 0; i < segments; i++) {
      var a0 = (i / segments) * Math.PI * 2;
      var a1 = ((i + 1) / segments) * Math.PI * 2;
      var p0 = transform(Math.cos(a0) * radius, Math.sin(a0) * radius, 0);
      var p1 = transform(Math.cos(a1) * radius, Math.sin(a1) * radius, 0);
      pts.push(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2]);
    }
    return new Float32Array(pts);
  }

  function createOrbitParticles(count, minR, maxR) {
    var data = [];
    for (var i = 0; i < count; i++) {
      var theta = (i / count) * Math.PI * 2 + (i * 0.37);
      var phi = Math.acos(1 - 2 * ((i + 0.5) / count));
      var r = minR + ((i * 73) % 100) / 100 * (maxR - minR);
      data.push(
        r * Math.sin(phi) * Math.cos(theta),
        r * Math.cos(phi) * 0.72,
        r * Math.sin(phi) * Math.sin(theta)
      );
    }
    return new Float32Array(data);
  }

  function makePerspective(fovRad, aspect, near, far) {
    var f = 1.0 / Math.tan(fovRad / 2);
    var nf = 1 / (near - far);
    return new Float32Array([
      f / aspect, 0, 0, 0,
      0, f, 0, 0,
      0, 0, (far + near) * nf, -1,
      0, 0, (2 * far * near) * nf, 0
    ]);
  }

  function makeRotationXY(rx, ry, tx, ty, tz, scale) {
    var cx = Math.cos(rx), sx = Math.sin(rx);
    var cy = Math.cos(ry), sy = Math.sin(ry);
    var s = scale || 1.0;
    return new Float32Array([
      cy * s, sx * sy * s, -cx * sy * s, 0,
      0, cx * s, sx * s, 0,
      sy * s, -sx * cy * s, cx * cy * s, 0,
      tx || 0, ty || 0, tz || 0, 1
    ]);
  }

  function ShellCraftThreeScene(canvas, options) {
    this.canvas = canvas;
    this.mode = (options && options.mode) || 'ssh';
    this.state = {
      primary: hexToRgbNorm((options && options.primary) || '#00E5FF'),
      secondary: hexToRgbNorm((options && options.secondary) || '#10F5A0'),
      bg: hexToRgbNorm((options && options.bg) || '#0D1322'),
      speed: 1.0,
      pulse: 0.25
    };
    this.target = {
      primary: this.state.primary.slice(),
      secondary: this.state.secondary.slice(),
      bg: this.state.bg.slice(),
      speed: 1.0,
      pulse: 0.25
    };
    this.rotX = 0.25;
    this.rotY = 0.0;
    this.velX = 0;
    this.velY = 0;
    this.dragging = false;
    this.lastPx = 0;
    this.lastPy = 0;
    this.running = true;
    this.initGl();
  }

  ShellCraftThreeScene.prototype.initGl = function () {
    var gl = this.canvas.getContext('webgl', { alpha: true, antialias: true, powerPreference: 'low-power' }) ||
             this.canvas.getContext('experimental-webgl', { alpha: true, antialias: true });
    if (!gl) return;
    this.gl = gl;

    var vsSrc = 'attribute vec3 aPos; uniform mat4 uProj; uniform mat4 uModel; uniform float uPointSize; void main(){ gl_Position = uProj * uModel * vec4(aPos, 1.0); gl_PointSize = uPointSize; }';
    var fsSrc = 'precision mediump float; uniform vec3 uColor; uniform float uAlpha; void main(){ gl_FragColor = vec4(uColor, uAlpha); }';

    function compile(type, src) {
      var sh = gl.createShader(type);
      gl.shaderSource(sh, src);
      gl.compileShader(sh);
      return sh;
    }

    var prog = gl.createProgram();
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, vsSrc));
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fsSrc));
    gl.linkProgram(prog);
    gl.useProgram(prog);
    this.prog = prog;

    this.uProj = gl.getUniformLocation(prog, 'uProj');
    this.uModel = gl.getUniformLocation(prog, 'uModel');
    this.uColor = gl.getUniformLocation(prog, 'uColor');
    this.uAlpha = gl.getUniformLocation(prog, 'uAlpha');
    this.uPointSize = gl.getUniformLocation(prog, 'uPointSize');
    this.aPos = gl.getAttribLocation(prog, 'aPos');
    gl.enableVertexAttribArray(this.aPos);

    this.coreBuf = this.makeBuf(createIcosahedronEdges(this.mode === 'forge' ? 0.92 : 0.85));
    this.ringABuf = this.makeBuf(createRingEdges(1.36, 48, 0.45, 0.2));
    this.ringBBuf = this.makeBuf(createRingEdges(1.62, 56, -0.55, -0.3));
    this.ptsBuf = this.makeBuf(createOrbitParticles(54, 1.05, 2.3));

    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);

    var self = this;
    this.canvas.addEventListener('pointerdown', function (e) {
      self.dragging = true;
      self.lastPx = e.clientX;
      self.lastPy = e.clientY;
    });
    window.addEventListener('pointermove', function (e) {
      if (!self.dragging) return;
      var dx = e.clientX - self.lastPx;
      var dy = e.clientY - self.lastPy;
      self.lastPx = e.clientX;
      self.lastPy = e.clientY;
      self.velY = dx * 0.012;
      self.velX = dy * 0.012;
      self.rotY += self.velY;
      self.rotX += self.velX;
    });
    window.addEventListener('pointerup', function () { self.dragging = false; });

    var start = performance.now();
    function frame(now) {
      if (!self.running) return;
      self.render((now - start) * 0.001);
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  };

  ShellCraftThreeScene.prototype.makeBuf = function (arr) {
    var gl = this.gl;
    var buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, arr, gl.STATIC_DRAW);
    return { buf: buf, count: arr.length / 3 };
  };

  ShellCraftThreeScene.prototype.setState = function (primaryHex, secondaryHex, bgHex, speed, pulse) {
    if (primaryHex) this.target.primary = hexToRgbNorm(primaryHex);
    if (secondaryHex) this.target.secondary = hexToRgbNorm(secondaryHex);
    if (bgHex) this.target.bg = hexToRgbNorm(bgHex);
    if (typeof speed === 'number') this.target.speed = speed;
    if (typeof pulse === 'number') this.target.pulse = pulse;
  };

  ShellCraftThreeScene.prototype.render = function (t) {
    var gl = this.gl;
    if (!gl) return;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var w = Math.max(1, Math.floor(this.canvas.clientWidth * dpr));
    var h = Math.max(1, Math.floor(this.canvas.clientHeight * dpr));
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    gl.viewport(0, 0, w, h);

    for (var i = 0; i < 3; i++) {
      this.state.primary[i] = lerp(this.state.primary[i], this.target.primary[i], 0.08);
      this.state.secondary[i] = lerp(this.state.secondary[i], this.target.secondary[i], 0.08);
      this.state.bg[i] = lerp(this.state.bg[i], this.target.bg[i], 0.12);
    }
    this.state.speed = lerp(this.state.speed, this.target.speed, 0.08);
    this.state.pulse = lerp(this.state.pulse, this.target.pulse, 0.08);

    gl.clearColor(this.state.bg[0], this.state.bg[1], this.state.bg[2], 0.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    if (!this.dragging) {
      this.rotY += 0.008 * this.state.speed + this.velY;
      this.rotX += this.velX;
      this.velX *= 0.92;
      this.velY *= 0.92;
    }

    var aspect = w / h;
    var proj = makePerspective(0.95, aspect, 0.1, 25.0);
    gl.uniformMatrix4fv(this.uProj, false, proj);

    var breath = 1.0 + Math.sin(t * 2.4 * this.state.speed) * (0.04 + 0.06 * this.state.pulse);
    var offsetX = aspect > 1.8 ? 1.35 : 0.72;

    // Inner wire icosahedron
    gl.bindBuffer(gl.ARRAY_BUFFER, this.coreBuf.buf);
    gl.vertexAttribPointer(this.aPos, 3, gl.FLOAT, false, 0, 0);
    gl.uniformMatrix4fv(this.uModel, false, makeRotationXY(this.rotX + Math.sin(t * 0.7) * 0.2, this.rotY, offsetX, 0, -4.1, breath));
    gl.uniform3fv(this.uColor, this.state.primary);
    gl.uniform1f(this.uAlpha, 0.85);
    gl.uniform1f(this.uPointSize, 1.0);
    gl.drawArrays(gl.LINES, 0, this.coreBuf.count);

    // Outer gyroscope ring A
    gl.bindBuffer(gl.ARRAY_BUFFER, this.ringABuf.buf);
    gl.vertexAttribPointer(this.aPos, 3, gl.FLOAT, false, 0, 0);
    gl.uniformMatrix4fv(this.uModel, false, makeRotationXY(this.rotX * 0.6 + t * 0.45 * this.state.speed, -this.rotY * 0.8, offsetX, 0, -4.1, 1.0));
    gl.uniform3fv(this.uColor, this.state.secondary);
    gl.uniform1f(this.uAlpha, 0.55);
    gl.drawArrays(gl.LINES, 0, this.ringABuf.count);

    // Outer gyroscope ring B
    gl.bindBuffer(gl.ARRAY_BUFFER, this.ringBBuf.buf);
    gl.vertexAttribPointer(this.aPos, 3, gl.FLOAT, false, 0, 0);
    gl.uniformMatrix4fv(this.uModel, false, makeRotationXY(-t * 0.32 * this.state.speed, this.rotY * 1.15, offsetX, 0, -4.1, 1.0));
    gl.uniform3fv(this.uColor, this.state.primary);
    gl.uniform1f(this.uAlpha, 0.35);
    gl.drawArrays(gl.LINES, 0, this.ringBBuf.count);

    // Orbiting telemetry particles
    gl.bindBuffer(gl.ARRAY_BUFFER, this.ptsBuf.buf);
    gl.vertexAttribPointer(this.aPos, 3, gl.FLOAT, false, 0, 0);
    gl.uniformMatrix4fv(this.uModel, false, makeRotationXY(Math.sin(t * 0.3) * 0.25, t * 0.35 * this.state.speed + this.rotY * 0.5, offsetX * 0.6, 0, -4.2, 1.0));
    gl.uniform3fv(this.uColor, this.state.secondary);
    gl.uniform1f(this.uAlpha, 0.78);
    gl.uniform1f(this.uPointSize, 4.2 * dpr);
    gl.drawArrays(gl.POINTS, 0, this.ptsBuf.count);
  };

  ShellCraftThreeScene.prototype.destroy = function () {
    this.running = false;
  };

  global.ShellCraftThreeScene = ShellCraftThreeScene;
})(window);
