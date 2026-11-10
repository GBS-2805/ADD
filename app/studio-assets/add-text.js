/*
 * Fabric+ text: a Textbox that owns its effects instead of relying on a pile of
 * unrelated properties. Gradient, outline and shadow are serialised with the object,
 * and fastMode lets a gesture drop the expensive passes until the finger lifts.
 */
(function () {
  'use strict';

  var AddText = fabric.util.createClass(fabric.Textbox, {
    type: 'AddText',

    initialize: function (text, options) {
      options = options || {};
      this.callSuper('initialize', text, options);

      var base = typeof options.startColour === 'string' ? options.startColour
        : (typeof options.fill === 'string' ? options.fill : '#15120e');
      this.startColour = base;
      this.endColour = options.endColour || '#dd4124';
      this.gradientOn = options.gradientOn !== false;
      this.gradientAngle = options.gradientAngle == null ? 90 : options.gradientAngle;
      this.outlineOn = options.outlineOn === true;
      this.outlineColour = options.outlineColour || '#15120e';
      this.outlineWidth = options.outlineWidth == null ? 6 : options.outlineWidth;
      this.shadowOn = options.shadowOn !== false;
      this.blendMode = options.blendMode || 'source-over';
      this.fastMode = false;
      this.applyEffects();
    },

    /** Rebuild the paint properties from the effect flags. Safe to call repeatedly. */
    applyEffects: function () {
      var w = Math.max(1, this.width || 1);
      var h = Math.max(1, this.height || this.fontSize || 1);
      if (this.gradientOn) {
        var angle = (this.gradientAngle || 0) * Math.PI / 180;
        var half = Math.sqrt(w * w + h * h) / 2;
        var cx = w / 2;
        var cy = h / 2;
        this.set('fill', new fabric.Gradient({
          type: 'linear',
          coords: {
            x1: cx - Math.cos(angle) * half,
            y1: cy - Math.sin(angle) * half,
            x2: cx + Math.cos(angle) * half,
            y2: cy + Math.sin(angle) * half
          },
          colorStops: [
            {offset: 0, color: this.startColour},
            {offset: 1, color: this.endColour}
          ]
        }));
      } else {
        this.set('fill', this.startColour);
      }

      this.set('stroke', this.outlineOn && this.outlineWidth > 0 ? this.outlineColour : null);
      this.set('strokeWidth', this.outlineOn ? this.outlineWidth : 0);
      this.set('paintFirst', 'stroke');
      this.set('strokeUniform', true);
      this.set('shadow', this.shadowOn ? new fabric.Shadow({
        color: 'rgba(0,0,0,0.42)',
        blur: 7,
        offsetX: 0,
        offsetY: 4
      }) : null);
      this.set('globalCompositeOperation', this.blendMode || 'source-over');
      this.dirty = true;
      if (this.canvas) {
        this.canvas.requestRenderAll();
      }
    },

    toObject: function (propertiesToInclude) {
      return fabric.util.object.extend(this.callSuper('toObject', propertiesToInclude), {
        startColour: this.startColour,
        endColour: this.endColour,
        gradientOn: this.gradientOn,
        gradientAngle: this.gradientAngle,
        outlineOn: this.outlineOn,
        outlineColour: this.outlineColour,
        outlineWidth: this.outlineWidth,
        shadowOn: this.shadowOn,
        blendMode: this.blendMode
      });
    }
  });

  AddText.fromObject = function (object, callback) {
    callback(new AddText(object.text || '', object));
  };

  fabric.AddText = AddText;
})();
