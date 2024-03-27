import os
import re
from io import BytesIO
from typing import Optional

import bottle
import requests
from PIL import Image
from PIL import ImageDraw
from PIL import ImageFilter
from PIL import ImageFont
from bottle import response


def kebabcase(s):
    return "-".join(re.findall(
        r"[A-Z]{2,}(?=[A-Z][a-z]+[0-9]*|\b)|[A-Z]?[a-z]+[0-9]*|[A-Z]|[0-9]+",
        s.lower()
    ))


class Data:
    def __init__(self, base_uri):
        self.base_uri = base_uri

    def _find_matching(self, uri, match) -> Optional[dict]:
        response = requests.get(uri)
        response.raise_for_status()
        data = response.json()

        wanted = [d for d in data if match(d)]

        if len(wanted):
            return wanted[0]
        else:
            return None

    def totals_for_beach(self, beach_slug) -> Optional[dict]:
        uri = f"{self.base_uri}/v1/2023/spills-by-beach.json"

        def matching(d):
            return d["reporting_year"] == 2023 and kebabcase(d["bathing"]) == beach_slug

        return self._find_matching(uri, matching)

    def totals_for_shellfishery(self, shellfish_slug) -> Optional[dict]:
        uri = f"{self.base_uri}/v1/2023/spills-by-shellfish.json"

        def matching(d):
            return kebabcase(d["shellfishery"]) == shellfish_slug

        return self._find_matching(uri, matching)


class BadgeDrawing:
    def __init__(self):
        self.big_font = ImageFont.truetype(font="Roboto-Medium.ttf", size=48)
        self.med_font = self.big_font.font_variant(size=32)
        self.small_font = self.big_font.font_variant(size=16)

    def create_beach_badge(self, beach, spills, company, year) -> Image.Image:
        background = Image.open("assets/beach.jpg").filter(ImageFilter.GaussianBlur(3))
        poop = Image.open("assets/poop.png")
        poop = poop.resize(size=(128, 128))

        image = Image.new("RGBA", (526, 263), (0, 0, 0, 0))

        image.paste(background)
        image.alpha_composite(poop, dest=(400, 120))

        draw = ImageDraw.Draw(image)

        def text(**kwargs):
            draw.text(fill=(255, 255, 255), stroke_fill=(0, 0, 0), stroke_width=2, **kwargs)

        text(xy=(10, 25 + 0), text=f"{beach}", font=self.big_font)
        text(xy=(10, 25 + 64), text=f"had sewage dumped on it\n{spills:,d} times\nby {company} in {year}", font=self.med_font)
        text(xy=(10, 230), text="top-of-the-poops.org", font=self.small_font)

        return image


    def create_shellfishery_badge(self, shellfishery, spills, company, year) -> Image.Image:
        background = Image.open("assets/shells.jpg").filter(ImageFilter.GaussianBlur(3))
        poop = Image.open("assets/poop.png")
        poop = poop.resize(size=(128, 128))

        image = Image.new("RGBA", (526, 263), (0, 0, 0, 0))

        image.paste(background)
        image.alpha_composite(poop, dest=(400, 120))

        draw = ImageDraw.Draw(image)

        def text(**kwargs):
            draw.text(fill=(255, 255, 255), stroke_fill=(0, 0, 0), stroke_width=2, **kwargs)

        text(xy=(10, 25 + 0), text=f"{shellfishery}", font=self.big_font)
        text(xy=(10, 25 + 64), text=f"shellfish polluted by sewage\n{spills:,d} times\nby {company} in {year}", font=self.med_font)
        text(xy=(10, 230), text="top-of-the-poops.org", font=self.small_font)

        return image


class BadgeApp:

    def __init__(self, app, data: Data, drawing: BadgeDrawing):
        self.app = app
        self.data = data
        self.drawing = drawing

        self.create_routes()

    def _respond(self, image: Image.Image) -> bytes:
        buffer = BytesIO()
        image.convert("RGB").save(buffer, "jpeg", optimize=True)

        response.status = 200
        response.content_type = 'image/jpg'
        return buffer.getvalue()

    def create_routes(self):
        @self.app.route("/beach/<beach>")
        def beach_badge(beach):
            info = self.data.totals_for_beach(beach)
            if info is None:
                response.status = 404
            else:
                return self._respond(
                    image=self.drawing.create_beach_badge(
                        beach=info["bathing"],
                        spills=int(info["total_spill_count"]),
                        company=info["company_name"],
                        year=int(info["reporting_year"])
                    )
                )

        @self.app.route("/shellfishery/<area>")
        def beach_badge(area):
            info = self.data.totals_for_shellfishery(area)
            if info is None:
                response.status = 404
            else:
                return self._respond(
                    image=self.drawing.create_shellfishery_badge(
                        shellfishery=info["shellfishery"],
                        spills=int(info["total_count"]),
                        company=info["company_name"],
                        year=2023
                    )
                )


if __name__ == '__main__':
    port = int(os.environ.get("PORT", 80))
    data_uri = os.environ.get("DATA_URI", "http://data/data")

    drawing = BadgeDrawing()
    data = Data(base_uri=data_uri)

    app = bottle.Bottle()

    BadgeApp(app=app, data=data, drawing=drawing)

    app.run(server='gunicorn', host='0.0.0.0', port=port)
